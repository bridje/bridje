(ns bridje.reader)

(defn newline? [ch]
  (contains? #{\newline \return} ch))

(defn read-chs [s]
  (letfn [(read-chs* [s loc]
            (lazy-seq
             (when-let [[ch & more-chs] (seq s)]
               (case ch
                 \return (let [[ch2 & even-more-chs] more-chs]
                           (cons {:ch \newline, :loc loc}
                                 (read-chs* (let [[ch2 & even-more-chs] more-chs]
                                              (case ch2
                                                \newline even-more-chs
                                                more-chs))
                                            {:line (inc (:line loc)), :col 1})))
                 \newline (cons {:ch ch, :loc loc}
                                (read-chs* more-chs {:line (inc (:line loc)), :col 1}))
                 (cons {:ch ch, :loc loc}
                       (read-chs* more-chs (update loc :col inc)))))))]

    (read-chs* s {:line 1 :col 1})))

(defrecord LocRange [start end])

(defn whitespace? [ch]
  (or (Character/isWhitespace ch)
      (= \, ch)))

(let [delimiters #{\( \) \[ \] \{ \} \; \#}]
  (defn delimiter? [ch]
    (or (whitespace? ch)
        (contains? delimiters ch))))

(do
  (defn tokenise [chs]
    (lazy-seq
     (when-let [[{:keys [ch loc]} & more-chs] chs]
       (case ch
         (\(\)\[\]\{\}\`\') {:type :delimiter, :token (str ch), :range (->LocRange loc loc)}

         )
       )))

  #_(tokenise "Hello \n world"))
