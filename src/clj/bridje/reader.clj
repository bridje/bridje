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
      [{:token-type :string
        :token (s/join (map :ch (str-escape sym-chs)))
        :loc-range (->LocRange start-loc (:loc (first more-chs)))}
       (rest more-chs)]

      (throw (ex-info "EOF reading string", {:loc (:loc (last sym-chs))})))))

(defn read-symbol-token [chs]
  (let [start-loc (:loc (first chs))
        [sym-chs more-chs] (split-with (comp (complement delimiter?) :ch) chs)]
    [{:token-type :symbol
      :token (s/join (map :ch sym-chs))
      :loc-range (->LocRange start-loc (:loc (last sym-chs)))}
     more-chs]))

(defn tokenise [chs]
  (lazy-seq
    (when-let [[{:keys [ch loc]} & more-chs :as chs] (slurp-whitespace chs)]
      (case ch
        (\( \[ \{) (cons {:token-type :start-delimiter, :token (str ch), :loc-range (->LocRange loc loc)} (tokenise more-chs))
        (\) \] \}) (cons {:token-type :end-delimiter, :token (str ch), :loc-range (->LocRange loc loc)} (tokenise more-chs))
        (\` \') (cons {:token-type :quote, :token (str ch), :loc-range (->LocRange loc loc)} (tokenise more-chs))

        \~ (let [[{:keys [ch], end-loc :loc} & even-more-chs] more-chs]
             (case ch
               (\@) (cons {:token-type :quote, :token "~@" :loc-range (->LocRange loc end-loc)}
                          (tokenise even-more-chs))
               (tokenise more-chs)))

        \# (let [[{:keys [ch], end-loc :loc} & even-more-chs] more-chs]
             (case ch
               (\{) (cons {:token-type :start-delimiter, :token (str "#{") :loc-range (->LocRange loc end-loc)}
                          (tokenise even-more-chs))
               (throw (ex-info "Unexpected character following '#'" {:ch (str "#" ch) :loc loc}))))

        \" (let [[res more-chs] (read-string-token chs)]
             (cons res (tokenise more-chs)))

        (let [[res more-chs] (read-symbol-token chs)]
          (cons res (tokenise more-chs)))))))

(do
  (defn parse-tokens [tokens]
    tokens)

  (defn read-forms [s]
    (-> s
        read-chs
        tokenise
        parse-tokens))

  (read-forms "Hello [\"World\" \"More\"]"))
