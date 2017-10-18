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

(def num-regex
  #"([-+])?(0x|0)?([\da-fA-F]+)(\.[\da-fA-F]+)?([MN])?")

(defn try-read-number-token [chs]
  (let [[num-chs more-chs] (split-with (comp (complement delimiter?) :ch) chs)
        loc-range (->LocRange (:loc (first num-chs)) (:loc (last num-chs)) )
        num-str (s/join (map :ch num-chs))
        e-invalid-number (ex-info "Invalid number"
                                  {:number num-str
                                   :loc-range loc-range})]

    (when-let [[_ sign base int-part fraction-part suffix] (re-matches num-regex num-str)]
      (try
        (when (or base (Character/isDigit (first int-part)))
          (let [num-str (str sign int-part fraction-part)
                radix (case base
                        "0x" 16
                        "0" 8
                        10)

                [number-type number] (cond
                                       suffix (case suffix
                                                "M" [:big-float (if (= radix 10)
                                                                  (BigDecimal. num-str)
                                                                  (throw e-invalid-number))]

                                                "N" [:big-int (clojure.lang.BigInt/fromBigInteger (BigInteger. num-str radix))])

                                       (nil? fraction-part) [:int (Long/parseLong num-str radix)]

                                       :else [:float (if (= radix 10)
                                                       (Double/parseDouble num-str)
                                                       (throw e-invalid-number))])]
            [{:token-type number-type
              :token number
              :loc-range loc-range}

             more-chs]))

        (catch Exception e
          (throw e-invalid-number))))))

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

        (let [[res more-chs] (or (try-read-number-token chs)
                                 (read-symbol-token chs))]
          (cons res (tokenise more-chs)))))))

(defn split-sym [{:keys [token loc-range]}]
  (let [[ns-or-sym sym & more] (s/split token #"/")]
    (if (seq more)
      (throw (ex-info "Multiple '/'s in symbol" {:symbol token, :loc-range loc-range}))
      (if sym
        {:form-type :namespaced-symbol
         :ns (symbol ns-or-sym)
         :sym (symbol sym)}
        {:form-type :symbol
         :sym (symbol ns-or-sym)}))))

(def delimiters
  {"(" {:end-delimiter ")"
        :form-type :list}
   "[" {:end-delimiter "]"
        :form-type :vector}
   "{" {:end-delimiter "}"
        :form-type :record}
   "#{" {:end-delimiter "}"
         :form-type :set}})

(defn parse-form [tokens]
  (letfn [(parse-form* [tokens end-delimiter]
            (if-let [[{:keys [token-type token loc-range]} & more-tokens] (seq tokens)]
              (case token-type
                :start-delimiter (let [{:keys [end-delimiter form-type]} (get delimiters token)]
                                   (loop [forms []
                                          tokens more-tokens]
                                     (let [[form remaining-tokens] (parse-form* tokens end-delimiter)]
                                       (if form
                                         (recur (conj forms form) remaining-tokens)

                                         [{:form-type form-type,
                                           :forms forms,
                                           :loc-range (->LocRange (:start loc-range)
                                                                  (:end (:loc-range (first remaining-tokens))))}
                                          (rest remaining-tokens)]))))

                :end-delimiter (if (= end-delimiter token)
                                 [nil tokens]
                                 (throw (ex-info "Unexpected end delimiter" {:expected end-delimiter
                                                                             :found token
                                                                             :loc-range loc-range})))

                :quote (let [[form remaining-tokens] (parse-form* more-tokens nil)]
                         (if-not form
                           (throw (ex-info "Unexpected EOF"))
                           [{:form-type (case token
                                          "'" :quote
                                          "`" :syntax-quote
                                          "~" :unquote
                                          "~@" :unquote-splicing)
                             :form form}
                            remaining-tokens]))

                :string [{:form-type :string, :string token, :loc-range loc-range} more-tokens]
                (:int :float :big-int :big-float) [{:form-type token-type, :number token, :loc-range loc-range} more-tokens]

                :symbol (case token
                          ("true" "false") [{:form-type :bool, :bool (Boolean/valueOf token), :loc-range loc-range} more-tokens]
                          [(split-sym {:token token, :loc-range loc-range})
                           more-tokens]))

              (when end-delimiter
                (throw (ex-info "Unexpected EOF" {:expected end-delimiter})))))]

    (parse-form* tokens nil)))

(defn parse-forms [tokens]
  (lazy-seq
    (when-let [[form more-tokens] (parse-form tokens)]
      (cons form (parse-forms more-tokens)))))

(defn read-forms [s]
  (-> s
      read-chs
      tokenise
      parse-forms))

(comment
  (read-forms "Hello '[\"World\" \"More\"]"))
