(ns rock-paper-stuff.play
  (:require [rock-paper-stuff.util :as u]))

(defn outcome 
  "Returns a vector of two vectors that contain what the player of play1 and
  the player of play2 will receive from the trade, respectively."
  [play1 play2]
  (if (= play1 play2)
    (rand-nth [[[play1 play2] []] [[] [play1 play2]]])
    (let [inverse #(reverse (outcome play2 play1))]
      (case play1
        :rock (case play2
                :paper [[:paper][:rock]]
                :scissors [[:fire] [:rock]]
                :fire [[:rock] [:scissors]] 
                :water [[] [:water :water]]) 
        :paper (case play2
                 :scissors [[:paper :paper][:scissors]]
                 :fire [[] [:fire :fire]]
                 :water [[:paper] []]
                 (inverse))
        :scissors (case play2
                    :fire [[:scissors :scissors] [:fire]]
                    :water [[:water] [:rock]]
                    (inverse))
        :fire (case play2
                :water [[] [:water]]
                (inverse))
        :water (inverse)))))

(defn update-player
  "Returns player updated to reflect the effects of the most recent trade."
  [player play memory skin products opponent opponent-play]
  (let [new-inventory (reduce #(update %1 %2 inc)
                              (update (:inventory player) play dec)
                              products)]
    (assoc player
      :inventory new-inventory
      :history (conj (:history player) 
                     {:name (:name opponent) 
                      :play play 
                      :opponent-play opponent-play})
      :memory (merge (:memory player) memory)
      :skin (merge (:skin player) skin)
      :alive (some #(pos? (second %)) new-inventory))))

(defn play-game
  "Plays a game of Rock Paper Stuff among the provided players. Returns 
  a map of the :winner (which is the :name of the highest-scoring player,
  or Nobody if there is no single winner), the collection of final
  :players in full detail, and the entire :global-history of the run."
  [players]
  (let [num-players (count players)
        inventories (fn [players]
                      (into {} (for [p players] 
                                 {(:name p) (:inventory p)})))]
    (loop [;;; play for n(n-1)/2 steps, where n is the number of players
            steps (* 100 (/ (* num-players (dec num-players)) 2))
            ;; players get as much of each kind of stuff as there are players
            players (mapv (fn [p]
                            (assoc p :inventory 
                              (apply hash-map 
                                     (interleave u/stuff 
                                                 (repeat (* 5 num-players))))
                              :alive true))
                          players)
            ;; the global-history is initially empty
            global-history [(inventories players)]]
      (let [alive (filter :alive players)]
        (if (or (zero? steps)
                (< (count alive) 2))
          ;; game over, return a report
          (let [dead (filter #(not (:alive %)) players)
                sorted (concat (sort-by u/deviance alive) dead)]
            {:winner (if (apply = (map u/deviance (take 2 sorted)))
                       "Nobody"
                       (:name (first sorted)))
             :players (mapv #(assoc % :deviance (u/deviance %)) sorted)
             :global-history global-history})
          ;; otherwise, two live players play
          (let [;;; pick 2 live players
                 shuffled (shuffle alive)
                 [player1 player2] (take 2 shuffled)
                 ;; call player functions to get plays, memories, and skins
                 {play1 :play memory1 :memory skin1 :skin} 
                 , ((:function player1) 
                    player1 
                    (assoc (:skin player2) 
                      :name (:name player2)
                      :inventory (:inventory player2)))
                 {play2 :play memory2 :memory skin2 :skin} 
                 , ((:function player2) 
                    player2 
                    (assoc (:skin player1) 
                      :name (:name player1)
                      :inventory (:inventory player1)))
                 ;; ensure plays are valid
                 play1 (u/legitimize play1 player1)
                 play2 (u/legitimize play2 player2)
                 ;; determine the outcomes of the trade
                 [products1 products2] (outcome play1 play2)
                 ;; update the players
                 new-player1 (update-player 
                               player1 play1 memory1 skin1 products1 
                               player2 play2)
                 new-player2 (update-player 
                               player2 play2 memory2 skin2 products2 
                               player1 play1)
                 new-players (concat (drop 2 shuffled)
                                     [new-player1 new-player2])]
            (recur (dec steps)
                   new-players
                   (-> global-history
                       (conj {:player (:name player1) :plays play1})
                       (conj {:player (:name player2) :plays play2})
                       (conj (inventories new-players))))))))))

(defn tournament
  "Plays the specified number of games of Rock Paper Stuff among the provided
  players, returning a sequence of [name #wins] pairs reflecting the results
  of the games. The returned sequence is sorted, with the winningest player
  at the beginning."
  [games players]
  (let [results (repeatedly games #(play-game players))]
    (reverse (sort-by val (frequencies (map :winner results))))))
