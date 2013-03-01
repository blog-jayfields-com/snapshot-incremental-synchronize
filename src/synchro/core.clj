(ns synchro.core)

;; server
(defonce server-list (atom []))

(defonce appender (atom nil))
(defonce appending-fiber (atom nil))
(defonce subscriber (atom identity))
(defonce synchro-fiber (atom nil))

(defn subscribe [f]
  (reset! subscriber f))

(defn publish-to-client [m]
  (@subscriber m))

(defn get-snapshot []
  (.execute @synchro-fiber
            #(publish-to-client {:type :snapshot :val @server-list})))

(defn append-to-list []
  (.execute @synchro-fiber
            #(do
               (when (< 9 (count @server-list))
                 (swap! server-list butlast))
               (let [incremental (rand-nth (range 100))]
                 (swap! server-list conj incremental)
                 (publish-to-client {:type :incremental :val incremental})))))

(defn server-start []
  (reset! synchro-fiber (doto (org.jetlang.fibers.ThreadFiber.) .start))
  (reset! appending-fiber (doto (org.jetlang.fibers.ThreadFiber.) .start))
  (reset! appender (.scheduleAtFixedRate @appending-fiber
                                         append-to-list 500 500
                                         java.util.concurrent.TimeUnit/MILLISECONDS)))

(defn server-stop []
  (.dispose @appender)
  (.dispose @appending-fiber)
  (.dispose @synchro-fiber))

;; client
(defonce client-list (atom nil))

(defn handle-update [{:keys [type val]}]
  (if (= type :snapshot)
    (reset! client-list val)
    (when @client-list
      (when (< 9 (count @client-list))
        (swap! client-list butlast))
      (swap! client-list conj val))))

(defn client-start []
  (subscribe handle-update)
  (get-snapshot))

(defn client-stop []
  (subscribe identity))

(comment
  (server-start)
  @server-list
  (reset! server-list [])
  (server-stop)

  (client-start)
  @client-list
  (client-stop)

  [@server-list @client-list (= @server-list @client-list)]
  )
