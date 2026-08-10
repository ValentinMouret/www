(ns site.build
  "Build the source articles into a static website."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [hiccup2.core :as hiccup]
            [markdown.core :as markdown])
  (:import (java.nio.file Files StandardCopyOption)
           (org.jsoup Jsoup)))

(def site-title "Valentin Mouret")
(def default-author "Valentin Mouret")

(defn- file-extension
  [file]
  (some-> (.getName (io/file file))
          (str/split #"\.")
          last
          str/lower-case))

(defn- article-file?
  [file]
  (= "md" (file-extension file)))

(defn- remove-surrounding-quotes
  [value]
  (let [value (str/trim value)]
    (if (and (>= (count value) 2)
             (contains? #{\' \"} (first value))
             (= (first value) (last value)))
      (subs value 1 (dec (count value)))
      value)))

(defn- parse-front-matter
  [source]
  (if-let [[_ header body] (re-matches #"(?s)\A---\R(.*?)\R---\R?(.*)\z" source)]
    [(into {}
           (keep (fn [line]
                   (when-let [[_ key value] (re-matches #"\s*([^:#]+):\s*(.*?)\s*" line)]
                     [(keyword (str/lower-case (str/trim key)))
                      (remove-surrounding-quotes value)]))
                 (str/split-lines header)))
     body]
    [{} source]))

(defn- humanize-slug
  [slug]
  (->> (str/split slug #"[-_]")
       (map str/capitalize)
       (str/join " ")))

(defn- relative-without-extension
  [source-dir file]
  (let [relative (.toString (.relativize (.toPath (io/file source-dir))
                                         (.toPath (io/file file))))]
    (str/replace relative #"\.[^.]+$" "")))

(defn- page-layout
  [{:keys [title date description author body]}]
  (let [page-title  (or title site-title)
        description (or description "")
        author      (or author default-author)]
    [:html {:lang "en"}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:meta {:name "author" :content author}]
      [:meta {:name "description" :content description}]
      [:title page-title " | " site-title]
      [:link {:rel "stylesheet" :href "/style.css"}]
      [:link {:rel "stylesheet" :href "/site.css"}]
      [:link {:rel "stylesheet"
              :href "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/styles/github.min.css"}]]
     [:body
      [:header.site-header
       [:a.site-name {:href "/"} site-title]
       [:nav.site-nav {:aria-label "Primary navigation"}
        [:a {:href "/"} "Home"]]]
      [:main.article
       [:header.article-header
        [:p.eyebrow "Blog"]
        [:h1 page-title]
        (when date
          [:p.post-meta
           [:time {:datetime date} date]
           [:span {:aria-hidden "true"} "·"]
           [:span "By " author]])]
       [:article.article-body (hiccup/raw body)]
       [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/highlight.min.js"}]
       [:script "hljs.highlightAll();"]]]]))

(defn- add-highlight-language-classes
  [html]
  (str/replace html #"<code class=\"([^\"]+)\"" "<code class=\"language-$1\""))

(defn- pretty-html
  [html]
  (let [document (Jsoup/parse html)]
    (doto (.outputSettings document)
      (.prettyPrint true)
      (.indentAmount 2))
    (.outerHtml document)))

(defn- render-layout
  [article]
  (pretty-html
   (str "<!doctype html>\n"
        (hiccup/html {:mode :html} (page-layout article)))))

(defn- copy-file!
  [source destination]
  (.mkdirs (.getParentFile (io/file destination)))
  (Files/copy (.toPath (io/file source))
              (.toPath (io/file destination))
              (into-array StandardCopyOption [StandardCopyOption/REPLACE_EXISTING])))

(defn- copy-site-assets!
  [output-dir]
  (doseq [asset ["index.html" "style.css" "site.css" "me.webp" "cv.pdf"]
          :let  [source (io/file asset)]
          :when (.isFile source)]
    (copy-file! source (io/file output-dir asset))))

(defn- build-article!
  [source-dir output-dir file]
  (let [[metadata source] (parse-front-matter (slurp file))
        slug              (relative-without-extension source-dir file)
        title             (or (:title metadata) (humanize-slug (.getName (io/file slug))))
        body              (-> source
                              markdown/md-to-html-string
                              add-highlight-language-classes)
        destination       (io/file output-dir "posts" slug "index.html")]
    (.mkdirs (.getParentFile destination))
    (spit destination (render-layout (assoc metadata :title title :body body)))
    destination))

(defn build-site!
  ([] (build-site! "content/articles" "public"))
  ([source-dir output-dir]
   (let [source-directory (io/file source-dir)
         articles         (if (.isDirectory source-directory)
                            (->> (file-seq source-directory)
                                 (filter #(.isFile %))
                                 (filter article-file?)
                                 sort)
                            [])]
     (.mkdirs (io/file output-dir))
     (copy-site-assets! output-dir)
     (doseq [article articles]
       (build-article! source-dir output-dir article))
     {:output-dir    output-dir
      :article-count (count articles)})))

(defn -main
  [& _]
  (let [{:keys [output-dir article-count]} (build-site!)]
    (println (format "Built %d article(s) in %s" article-count output-dir))))
