(ns adventure.network
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json])
  (:import [java.net ServerSocket])
  (:import [java.net Socket])
  (:gen-class))

(declare NeedUpdate)
(declare UpdateState)
; TCP server
(defn Receive
  "Read a line of textual data from the given socket"
  [socket]
  (json/read-str (.readLine (io/reader socket)) :key-fun keyword))

(defn Send
  "Send the given string message out over the given socket"
  [socket msg]
  (let [writer (io/writer socket)]
    (.write writer (json/write-str msg))
    (.flush writer)))

; TCP server
; https://github.com/clojure-cookbook/clojure-cookbook/blob/master/05_network-io/5-10_tcp-server.asciidoc
; (reset! running false) to deactivate
; (def a (serve-persistent 8888 #(.toUpperCase %)))
(defn ServePersistent [port handler init_state & [timeout]]
  (let [running (atom true)]
    (future
      (with-open [server-sock (ServerSocket. port)
                  _ (when timeout (.setSoTimeout server-sock timeout))]
        (loop [state init_state]
          (when @running
            (let
             [ret
              (with-open [sock (.accept server-sock)]
                (handler sock state))]
              (recur ret))))))
    running))

(defn CommServerHandler [sock state]
  (let [Write #(Send sock %),
        Read #(Receive sock),
        tmp (Read),
        rdon (:rdon tmp),
        update (NeedUpdate (:tick tmp) (:tick state) (:self tmp)),
        _ (if update
            (Write {:update update, :newtick (:tick state), :newstate state})
            (Write {:update update}))]
    (if rdon state (Read))))

(defn CommClientGen [self port & [host]]
  (let [host (if host host 'localhost')]
    (fn [proc, state, rdon] ; para list
      (with-open
       [sock (Socket. host port),
        Write #(Send sock %),
        Read #(Receive sock),
        tick (:tick state),
        _ (Write {:rdon rdon, :tick (:tick state), :self self}),
        tmp (Read),
        update (:update tmp),
        state (if (update)
                (UpdateState state (:newstate tmp))
                state),
        newtick (:newtick state),
        ret (proc state)]
        (if rdon
          ret
          (do
            (Write {:newstate ret, :newtick newtick})
            ret))))))

(defn UpdateState [state newstate])

(defn NeedUpdate [tick, newtick, self]
  (let
   [cmpfun #(< (tick %) (newtick %)),
    cmpkey (->> tick keys (filter #(not= self %)))]
    (some cmpfun cmpkey)))