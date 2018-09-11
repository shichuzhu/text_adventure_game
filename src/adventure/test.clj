(ns adventure.test
  (:require [clojure.string :as str]
            [clojure.test :refer [testing is]]
            [clojure.java.io :as io]
            [adventure.network :as nw]
            [clojure.data.json :as json])
  (:import [java.net Socket])
  (:import [java.net ServerSocket])
  (:gen-class))

(defn UnitTests_Done []
  (testing "Utility functions"
    (is (=
         (nw/NeedUpdate {1 2 3 4} {1 3 3 3} 1)
         nil))
    (is (=
         (nw/NeedUpdate {1 2 3 4} {1 3 3 3} 3)
         true)))

  (testing "Server set up"
    (let [running
          (nw/ServePersistent
           23333
           (fn [sock state]
             (is (= 12345 (nw/Receive sock))))
           1)]
      (do
        ; (Thread/sleep 200) ; wait for the server to set up since it's async
        (with-open [sock (Socket. "localhost" 23333)]
          (reset! running false)
          (nw/Send sock 12345))))

    (let [running
          (nw/ServePersistent
           23334
           (fn [sock state]
             (let [input (nw/Receive sock), _ (prn state)]
               (+ state input)))
           1 5000)]
      (do
        ; (Thread/sleep 200) ; wait for the server to set up since it's async
        (with-open [sock (Socket. "localhost" 23334)]
          (do
            ; (Thread/sleep 200) ; wait for the server to set up since it's async
            (nw/Send sock 1)))
        (with-open [sock (Socket. "localhost" 23334)]
          (do
            ; (Thread/sleep 200) ; wait for the server to set up since it's async
            (nw/Send sock 2)))
        (with-open [sock (Socket. "localhost" 23334)]
          (do
            ; (Thread/sleep 200) ; wait for the server to set up since it's async
            (nw/Send sock 3)))))

    ; (let [running
    ;       (nw/ServePersistent
    ;        23335
    ;        (fn [sock state]
    ;          (let [input (nw/Receive sock), _ (prn state input),
    ;                _ (Thread/sleep 1000), _ (prn "slept")
    ;                input (nw/Receive sock), _ (prn state input),
    ;                _ (Thread/sleep 1000), _ (prn "slept")
    ;                input (nw/Receive sock), _ (prn state input),
    ;                _ (Thread/sleep 1000), _ (prn "slept")
    ;                input (nw/Receive sock), _ (prn state input)]
    ;            (+ state input)))
    ;        1 5000)]
    ;     ; (Thread/sleep 200) ; wait for the server to set up since it's async
    ;   (with-open [sock (Socket. "localhost" 23335)]
    ;     (do
    ;       (Thread/sleep 1000) ; wait for the server to set up since it's async
    ;       (nw/Send sock 1)
    ;       (Thread/sleep 1000) ; wait for the server to set up since it's async
    ;       (nw/Send sock 2)
    ;       (Thread/sleep 1000) ; wait for the server to set up since it's async
    ;       (nw/Send sock 3)
    ;       (Thread/sleep 1000) ; wait for the server to set up since it's async
    ;       (nw/Send sock 4))))
          ;
)

  ; End of UnitTests function
)

(defn UnitTests []
  (testing "Basic socket utility"
    (with-open
     [server-sock (ServerSocket. 23336)]
     (let [
      future_val
      (future
        (with-open [sock (.accept server-sock)]
          (do
          (nw/Send sock "from the server")
          (nw/Receive sock))))
      ; _ (Thread/sleep 200),
      input "123456",
      _ (with-open [client (Socket. "localhost" 23336)]
      (do
       (.setSoTimeout client 500)
          (prn (nw/Receive client))
          (nw/Send client input)))
      _ (.setSoTimeout server-sock 5000)]
      ; (is (= "123456" "123456")))
      (is (= @future_val input)))
      ;
      )))

(defn mytest []

  (let [running
        (nw/ServePersistent
         23335
         (fn [sock state]
           (let [input (nw/Receive sock), _ (prn state input)]
             input))
         1 5000)]
        ; (Thread/sleep 200) ; wait for the server to set up since it's async
    (with-open [sock (Socket. "localhost" 23335)]
      (nw/Send sock :3)
      (nw/Send sock :4))))