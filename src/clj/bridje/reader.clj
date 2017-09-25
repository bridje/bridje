(ns bridje.reader
  (:require [clojure.string :as s]))

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
  (defn slurp-whitespace [chs]
    (when-let [[{:keys [ch]} & more-chs] (seq chs)]
      (cond
        (whitespace? ch) (recur (drop-while (comp whitespace? :ch) more-chs))
        (= \; ch) (recur (drop-while (comp (complement newline?) :ch) more-chs))
        :else chs)))

  (defn str-escape [chs]
    (lazy-seq
      (when-let [[{:keys [ch loc]} & more-chs] (seq chs)]
        (case ch
          \\ (let [[{:keys [ch]} & more-chs] more-chs]
               (cons {:ch (case ch
                            \n \newline
                            \t \tab
                            \r \return
                            \\ \\
                            \" \"
                            (throw (ex-info "Unexpected escape character in string" {:loc loc})))
                      :loc loc}
                     (str-escape more-chs)))

          (cons {:ch ch, :loc loc} (str-escape more-chs))))))

  (defn read-string-token [chs]
    (let [start-loc (:loc (first chs))
          [sym-chs more-chs] (split-with (comp (complement #{\"}) :ch) (rest chs))]
      (if (seq more-chs)
        [{:type :string
          :token (s/join (map :ch (str-escape sym-chs)))
          :loc-range (->LocRange start-loc (:loc (last sym-chs)))}
         (rest more-chs)]

        (throw (ex-info "EOF reading string", {:loc (:loc (last sym-chs))})))))

  (defn read-symbol-token [chs]
    (let [start-loc (:loc (first chs))
          [sym-chs more-chs] (split-with (comp (complement delimiter?) :ch) chs)]
      [{:type :symbol
        :token (s/join (map :ch sym-chs))
        :loc-range (->LocRange start-loc (:loc (last sym-chs)))}
       more-chs]))

  (defn tokenise [chs]
    (lazy-seq
      (when-let [[{:keys [ch loc]} & more-chs :as chs] (slurp-whitespace chs)]
        (case ch
          (\(\)\[\]\{\}\`\') {:type :delimiter, :token (str ch), :range (->LocRange loc loc)}

          \# (throw (ex-info "niy" {}))

          \" (let [[res chs] (read-string-token chs)]
               (cons res (tokenise chs)))

          (let [[res chs] (read-symbol-token chs)]
            (cons res (tokenise chs)))))))

  (defn parse [s]
    (tokenise (read-chs s)))

  (parse "Hello \n \"World \\n String\""))
