(ns adventure.core
  (:require [clojure.core.match :refer [match]]
            [clojure.string :as str]
            [clojure.set :refer [intersection subset? rename-keys]]
            [clojure.data.json :as json]
            [adventure.test :as tt :refer [UnitTests]]
            [adventure.network :as nw])
  (:gen-class))

(def adventurer
  (let [the-map (json/read-str (slurp "maps.json") :key-fn keyword),
    id_sym :p0]
  ; starting location
    {:location (-> the-map :_configuration :StartingLocation keyword)
     :inventory (-> the-map :_configuration :StartingInventory set)
    ;  :inventory (-> ["223-A+", "225-A+", "241-A+"] set)
     :quests {}
     ; clojure doesn't allow using key-value of the same map within definition
     :id id_sym
     :tick {id_sym, 0}
   ; Notice the map is also part of player status
     :the-map the-map
     :seen #{}}))

(defn status [player]
  (let [location (player :location)]
    (print (str "You are " (-> player :the-map location :title) ". "))
    (when-not ((player :seen) (name location))
      (println (-> player :the-map location :desc))
      ; Do NOT use side-effect function in map (lazy sequence) like this:
      ; (map #(println (:hint-text (val %))) (-> player :the-map location :events))
      (let [toprt (map #(:hint-text (val %)) (-> player :the-map location :events))]
        (when-not (empty? toprt)
          (println (apply str (interpose "\n" toprt))))))
    (update-in player [:seen] #(conj % (name location)))))

(defn to-keywords [commands]
  (mapv keyword (str/split commands #"[.,?! ]+")))

(defn go [dir player]
  "Move to map specified by dir"
  (let [location (player :location)
        dest (->> player :the-map location :dir dir keyword)]
    (if (nil? dest)
      (do (println "You can't go that way.")
          player)
      (assoc-in player [:location] dest))))

(defn tock [player]
  "Advance the counter for the # of commands player types"
  ; Update a value in a multi-hierarchy map data structure
  (let [id (:id player)] (update-in player [:tick id] inc)))

(defn RemoveQuest [quest player]
  "Accomplish a quest and remove it from quest queue"
  (let [kw (keyword quest),
        questdata (-> player :the-map :_quests kw)]
    (if (and questdata (-> player :quests kw))
      (do
        (println (str "Quest accomplished: " (name kw) " : " (-> player :quests kw)))
        (update-in player [:quests] #(dissoc % kw)))
      player)))

(defn AddQuest [quest player]
  "Add a quest"
  (let [kw (keyword quest),
        questdata (-> player :the-map :_quests kw)]
    (if (and questdata (-> player :quests kw not))
      (let [ret
            (update-in player [:quests] #(assoc % kw (-> player :the-map :_quests kw))),
            _ (println (str "New quest : " (name kw) " : " (-> ret :quests kw)))]
        ret)
      player)))

(defn SaveGame [fn_list, player]
  "Save the current state of game to a file"
  ; (doc name)
  (let [filename (apply str (interpose "." (map name fn_list)))]
    (do
      (with-open [wrtr (clojure.java.io/writer filename)]
        (.write wrtr (json/write-str player)))
      (print (str "Saved as file: " filename "\n")))))

(defn str2kw [map keychain]
  (update-in map keychain keyword))

(defn vec2set [vec]
  (into #{} vec))

(defn LoadGame [fn_list]
  "Load a saved game"
  ; (doc name)
  (let [filename (apply str (interpose "." (map name fn_list)))]
    (let [return (json/read-str (slurp filename) :key-fn keyword)
      ;; Fix some format loss due to json conversion
          ; conversion from type string to keyword
          return (reduce str2kw [return [:location] [:id]])
          ; conversion from type vector to set
          ; Note the contents of :seen will still be string, not keyword
          return (reduce
                  #(update-in %1 %2 vec2set)
                  [return [:inventory] [:seen]])
          _ (print (str "Loaded file: " filename "\n"))]
      return)))

(defn PickItem [in_list, player]
  "Pick item available in the map and add it to the inventory"
  (let [itemname (apply str (interpose "-" (map name in_list)))]
    (if (not ((player :inventory) itemname))
      (let [map_contents
            (let [tmp (player :location)]
              (-> player :the-map tmp :contents))
            index (.indexOf map_contents itemname)]
        (if (not (= index -1))
          ;; remove item from map contents
          (let [tmp
                (update-in
                 player
                 (let [tmp (player :location)]
                   [:the-map tmp :contents]) #(remove (fn [x] (= itemname x)) %))
          ;; add item to inventory
                tmp (update-in tmp [:inventory] #(conj % itemname))]
          ;; Add quest that is related to the item
            (let [questname (-> player :the-map :_itemAttr ((keyword itemname)) :questNew)]
              (if questname
                (AddQuest questname tmp)
                tmp)))
          (do
            (println (str itemname " does not exist in the area."))
            player)))
      (do
        ; for simplicity, all items in the world have unique names.
        (println (str "Can't pick " itemname ", item already exists in the inventory."))
        player))))

(defn DropItem [in_list, player]
  "Drop item in inventory to the current map"
  (let [itemname (apply str (interpose "-" (map name in_list)))]
    (if ((player :inventory) itemname)
      (let [map_contents
            (let [tmp (player :location)]
              (-> player :the-map tmp :contents))
            index (.indexOf map_contents itemname)]
        (if (= index -1)
          ;; add item to map contents
          (let [tmp
                (update-in
                 player
                 (let [tmp (player :location)]
                   [:the-map tmp :contents]) #(conj % itemname))]
          ;; remove item from inventory
            (update-in tmp [:inventory] #(disj % itemname)))
          (do
            (println (str "Can't drop " itemname ", item already exists in the area."))
            player)))
      (do
        (println (str itemname " is not in the inventory!"))
        player))))

(defn PrintInventory [player]
  "Print current player inventory"
  (do
    (print "Here is the inventory:\n")
    (print (str (:inventory player) "\n"))
    player))

(defn LookMap [player]
  "Respond to l/look command, print current map, item pickable, and available directions to go"
  (let [ret
        (update-in player [:seen] #(disj % (-> player :location name)))
        ; for some reason (player :location) has to be double bracketed
        _ (let [contents (-> player :the-map ((player :location)) :contents),
                directions (-> player :the-map ((player :location)) :dir)]
            (do
              (when contents
                (do
                  (print (str "Available items in this area: "))
                  (println contents)))
              (when directions
                (do
                  (print (str "Places to go from here: "))
                  (println directions)))))]
    ret))

(defn ObserveItem [in_list player]
  "Get :desc attribute of item"
  (let [item (apply str (interpose "-" (map name in_list)))]
    (when (contains? (player :inventory) item)
      ; (prn item)
      (prn (-> player :the-map :_itemAttr ((keyword item)) :desc)))
    player))

(defn EventMatcher [command player]
  "Match user input command with keyword of a event, return true when success"
  (let [kw_set (set (map name command)),
        event_dicts (-> player :the-map ((player :location)) :events),
        match_fun
        (fn [event_entry]
          (let [kws (:match-words (val event_entry))]
            (-> (intersection (set kws) kw_set) empty? not
              ;; the required condition should be among the inventory
                (and (-> event_entry val :require set (subset? (-> player :inventory)))))))]
    (let [result (first (filter match_fun event_dicts))]
      (when result
        (-> result val)))))

(defn ApplyEvent [entry player]
  "evaluate the function from the JSON and update the player"
  (let [action_list (:action entry),
        action_fun_list
        (map
         ; the function converting string in json to functional operator on player
         (fn [eventstr] (eval (read-string (str "(fn [player] (update-in player " eventstr "))"))))
         action_list),
        ret (reduce #(%2 %1) (conj action_fun_list player)),
        output (:post-text entry),
        _ (when output (prn output)),
        ;; Update quest info
        ret (let [questname (entry :questDone)]
              (if questname
                (RemoveQuest questname ret)
                ret)),
        ret (let [questname (entry :questNew)]
              (if questname
                (AddQuest questname ret)
                ret))]
    ret))

(defn PrintQuests [player]
  "Print active quests"
  (do
    (print (str "Active quests:\n"))
    (doseq [x (-> player :quests)] (println x))
    player))

(defn respond [player command]
  "Respond to command and return updated player"
  ;; Update the timestamp
  (let [player (tock player)]
    (match command

      ;; check status of current map
      [(:or :l :look)] (LookMap player)
      ;; check current quests
      [(:or :quest :quests :q)] (PrintQuests player)

      ;; move location
      ; https://github.com/clojure/core.match/wiki/Basic-usage#or-patterns
      ; TODO add support for go north
      [(:or :n :north)] (go :north player)
      [(:or :s :south)] (go :south player)
      [(:or :w :west)] (go :west player)
      [(:or :e :east)] (go :east player)

      ;; save/load game
      ([:save & r] :seq) (do (SaveGame r player) player)
      ([:load & r] :seq) (LoadGame r)

      ;; interact with inventory
      ([(:or :pick :get) & r] :seq) (PickItem r player)
      ([(:or :drop :throw) & r] :seq) (DropItem r player)
      [(:or :i :inv :inventory)] (PrintInventory player)

      ;; interact with items in the inventory
      ([:observe & r] :seq) (ObserveItem r player)

      ;; Search for matching event in the current map, otherwise command not understood
      _ (let [action_list (EventMatcher command player)]
          (if action_list
            (ApplyEvent action_list player)
            (do (println "I don't understand you.")
                player))))))

(defn -main
  "I don't do a whole lot ... still."
  [& args]
  (loop [local-player
         (AddQuest (-> adventurer :the-map :_configuration :StartingQuests first) adventurer)]
    (when-not (-> local-player :_configuration :finished)
      (let [pl (status local-player),
            _ (def current pl), ; for debug purpose
            _  (do (print "What do you want to do?\n--Lamport timestamps ") (print (:tick pl)) (print ":$ ") (flush)),
            command (read-line)]
        (recur (respond pl (to-keywords command)))))))

; lein repl :connect localhost:33931
; (load-file "/media/shichu/Data/shared/gmaildrive/workspace/byclass/cs225/honor/szhu28/adventure/src/adventure/core.clj")
