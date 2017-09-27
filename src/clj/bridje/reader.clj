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
               (cons {:token-type :quote, :token "~" :loc-range (->LocRange loc loc)}
                     (tokenise more-chs))))

        \# (let [[{:keys [ch], end-loc :loc} & even-more-chs] more-chs]
             (case ch
               (\{) (cons {:token-type :start-delimiter, :token (str "#{") :loc-range (->LocRange loc end-loc)}
                          (tokenise even-more-chs))
               (throw (ex-info "Unexpected character following '#'" {:ch (str "#" ch) :loc loc}))))

        \" (let [[res more-chs] (read-string-token chs)]
             (cons res (tokenise more-chs)))

        (let [[res more-chs] (read-symbol-token chs)]
          (cons res (tokenise more-chs)))))))

(defn split-sym [{:keys [token loc-range]}]
  (let [[ns-or-sym sym & more] (s/split token #"/")]
    (if (seq more)
      (throw (ex-info "Multiple '/'s in symbol" {:symbol token, :loc-range loc-range}))
      {:ns (when sym ns-or-sym)
       :sym (or sym ns-or-sym)})))

(def delimiters
  {"(" {:end-delimiter ")"
        :form-type :list}
   "[" {:end-delimiter "]"
        :form-type :vector}
   "{" {:end-delimiter "}"
        :form-type :record}
   "#{" {:end-delimiter "}"
         :form-type :set}})

(defn parse-form [tokens end-delimiter]
  (if-let [[{:keys [token-type token loc-range]} & more-tokens] (seq tokens)]
    (case token-type
      :start-delimiter (let [{:keys [end-delimiter form-type]} (get delimiters token)]
                         (loop [forms []
                                tokens more-tokens
                                loc-range loc-range]
                           (let [[form remaining-tokens] (parse-form tokens end-delimiter)]
                             (if form
                               (recur (conj forms form) remaining-tokens (assoc loc-range :end (get-in form [:loc-range :end])))
                               [{:form-type form-type, :forms forms, :loc-range loc-range} remaining-tokens]))))

      :end-delimiter (if (= end-delimiter token)
                       [nil more-tokens]
                       (throw (ex-info "Unexpected end delimiter" {:expected end-delimiter
                                                                   :found token
                                                                   :loc-range loc-range})))

      :quote (let [[form remaining-tokens] (parse-form more-tokens nil)]
               (if form
                 [{:form-type :list
                   :forms [{:form-type :symbol,
                            :ns "bridje.kernel"
                            :sym (case token
                                   "'" "quote"
                                   "`" "syntax-quote"
                                   "~" "unquote"
                                   "~@" "unquote-splicing")}
                           form]}
                  remaining-tokens]
                 (throw (ex-info "Unexpected EOF"))))

      :string [{:form-type :string, :string token} more-tokens]

      :symbol (case token
                ("true" "false") [{:form-type :bool, :bool (Boolean/valueOf token), :loc-range loc-range} more-tokens]
                [(merge {:form-type :symbol, :loc-range loc-range}
                        (split-sym {:token token, :loc-range loc-range}))
                 more-tokens]))

    (when end-delimiter
      (throw (ex-info "Unexpected EOF" {:expected end-delimiter})))))

(defn parse-forms [tokens]
  (lazy-seq
    (when-let [[form more-tokens] (parse-form tokens nil)]
      (cons form (parse-forms more-tokens)))))

(defn read-forms [s]
  (-> s
      read-chs
      tokenise
      parse-forms))

(comment
  (read-forms "Hello '[\"World\" \"More\"]"))
