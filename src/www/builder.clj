(ns www.builder
  "Builds the static site for www.valentinmouret.io"
  (:require [clojure.java.io :as io]
            [markdown-to-hiccup.core :as markdown]
            [hiccup2.core :as hiccup]
            [clojure.string :as str])
  (:import [org.jsoup Jsoup]))

(def posts-path "posts")

;; utils
(defn list-files
  [dir]
  (.listFiles dir))

(def posts
  (list-files (io/file posts-path)))

(defn pretty-print-html
  [^String html]
  (-> html
      Jsoup/parse
      (.outerHtml)))

(defn page
  [content]
  [:html
   [:head
    [:meta {:charset "UTF-8"}]
    [:link {:href "/style.css" :rel "stylesheet"}]]
   [:body
    [:div#nav
     [:div.centered
      [:div "Home"]]]
    [:div#content.centered
     content]]])

(defn clean-out
  []
  (doseq [file (->> (io/file "public")
                    (list-files)
                    (filter #(and (not (.isDirectory %))
                                  (.endsWith (.getName %) ".html"))))]
    (.delete file)))

(clean-out)

(doseq [post posts]
  (let [out (-> post
                slurp
                markdown/md->hiccup
                page
                hiccup/html
                str
                ;pretty-print-html
                )]
    (spit (str "public/" (str/replace (.getName post) #"\.md$" ".html")) out)))
