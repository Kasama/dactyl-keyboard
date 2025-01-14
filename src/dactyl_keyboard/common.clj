(ns dactyl-keyboard.common
  (:refer-clojure :exclude [use import])
  (:require [clojure.core.matrix :refer [mmul]]
            [scad-clj.scad :refer :all]
            [scad-clj.model :refer :all]))

; common parts between the two boards.

(defn extra-width
  "extra width between two keys in a row."
  [c]
  (let [nrows (get c :configuration-nrows 5)]
    (if (> nrows 5) 3.5 2.5)))
(def extra-height
  "extra height between two keys in a column."
  1.0)

(def keyswitch-height
  "the y dimension of an mx style keyswitch, in millimeter."
  14.0)
(def keyswitch-width
  "the x dimension of an mx style keyswitch, in millimeter."
  14.0)

(def alps-width
  "the x dimension of an alps style keyswitch, in millimeter."
  15.6)
(def alps-notch-width
  15.5)
(def alps-notch-height
  1)
(def alps-height
  "the y dimension of an alps style keyswitch, in millimeter."
  13)

(def sa-profile-key-height 12.7)
(def choc-profile-key-height 3.5)

(def plate-thickness 5)
(def mount-width (+ keyswitch-width 3.5))
(def mount-height (+ keyswitch-height 3.5))

(defn profile-key-height [switch-type] (case switch-type :choc choc-profile-key-height sa-profile-key-height))

(defn cap-top-height [switch-type]
  (+ plate-thickness (profile-key-height switch-type)))

;;;;;;;;;;;;;;;;;
;; placement function ;;
;;;;;;;;;;;;;;;;;

(defn dm-column-offset
  "Determines how much 'stagger' the columns are for dm.
   0 = inner index finger's column.
   1 = index finger's column.
   2 = middle finger's column.
   3 = ring finger's column.
   4 >= pinky finger's column.
   [x y z] means that it will be staggered by 'x'mm in X axis (left/right),
   'y'mm in Y axis (front/back), and 'z'mm in Z axis (up/down). "
  [c column]
  (let [stagger?       (get c :configuration-stagger?)
        stagger-index  (get c :configuration-stagger-index)
        stagger-middle (get c :configuration-stagger-middle)
        stagger-ring   (get c :configuration-stagger-ring)
        stagger-pinky  (get c :configuration-stagger-pinky)]
    (if stagger?
      (cond (= column 2) stagger-middle
            (= column 3) stagger-ring
            (>= column 4) stagger-pinky
            :else stagger-index)
      (cond (= column 2)  [0   0    -6.5]
            (>= column 4) [0   0     6]
            :else         [0   0     0]))))

(defn fcenterrow
  "Determines where should the center (bottom-most point in the row's curve)
   of the row located at. And most people would want to have the center
   at the homerow. Why does it subtract the value by 3? Because this codebase
   starts the row from the higher row (F row -> num row -> top row)
   and the homerow is number 3 from the last after thumb and bottom row."
  [nrows]
  (let [subtractor (case nrows
                     3 2.5
                     2 2
                     3)]
    (- nrows subtractor)))

(defn flastrow
  "Determines where the last row should be located at."
  [nrows]
  (- nrows 1))
(defn fcornerrow
  "Determines where the penultimate row should be located at."
  [nrows]
  (- nrows 2))
(defn fmiddlerow
  "Should be replaced with `fcenterrow`."
  [nrows]
  (- nrows 3))
(defn flastcol
  "Determines where the last column should be located at. With 0 being inner index
   finger, 1 being index finger, and so on."
  [ncols]
  (- ncols 1))

(defn frow-radius
  "It computes the radius of the row's curve. It takes the value of `pi` divided
   by `alpha` to compute the said radius."
  [alpha switch-type]
  (+ (/ (/ (+ mount-height extra-height) 2)
        (Math/sin (/ alpha 2)))
     (cap-top-height switch-type)))

(defn fcolumn-radius
  "It computes the radius of the column's curve. It takes the value of `pi` divided
   by `beta` to compute the said radius."
  [c beta switch-type]
  (+ (/ (/ (+ mount-width (extra-width c)) 2)
        (Math/sin (/ beta 2)))
     (cap-top-height switch-type)))

; when set `use-wide-pinky?`,
; you will get 1.5u keys for the outermost pinky keys.
(defn offset-for-column
  "This function is used to give additional spacing for the column.
   Main use case is to make the outer pinky keys use 1.5u."
  [c col row]
  (let [use-wide-pinky? (get c :configuration-use-wide-pinky?)
        nrows           (get c :configuration-nrows 5)
        ncols           (get c :configuration-ncols)
        lastrow         (flastrow nrows)
        lastcol         (flastcol ncols)]
    (if (and use-wide-pinky?
             (not= row lastrow)
             (= col lastcol))
      5.5
      0)))

; this is the helper function to 'place' the keys on the defined curve
; of the board.
(defn apply-key-geometry
  "Helps to place the keys in the determined where a key should be placed
   and rotated in xyz coordinate based on its position (row and column).
   It is the implementation detail of `key-place`."
  [c translate-fn rotate-x-fn rotate-y-fn column row shape]
  (let [original-alpha    (get c :configuration-alpha)
        pinky-alpha       (get c :configuration-pinky-alpha original-alpha)
        alpha             (if (>= column 4) pinky-alpha original-alpha)
        beta              (get c :configuration-beta)
        centercol         (get c :configuration-centercol 2)
        centerrow         (fcenterrow (get c :configuration-nrows 5))
        tenting-angle     (get c :configuration-tenting-angle)
        switch-type       (get c :configuration-switch-type)
        keyboard-z-offset (get c :configuration-z-offset)
        web-thickness     (get c :configuration-web-thickness)
        rotate-x-angle    (get c :configuration-rotate-x-angle)
        column-angle      (* beta (- centercol column))
        placed-shape      (->> shape
                               (translate-fn [(offset-for-column c
                                                                 column
                                                                 row)
                                              0
                                              (- (frow-radius alpha switch-type))])
                               (rotate-x-fn  (* alpha (- centerrow row)))
                               (translate-fn [0 0 (frow-radius alpha switch-type)])
                               (translate-fn [0 0 (- (fcolumn-radius c beta switch-type))])
                               (rotate-y-fn  column-angle)
                               (translate-fn [0 0 (fcolumn-radius c beta switch-type)])
                               (translate-fn (dm-column-offset c column)))]
    (->> placed-shape
         (rotate-y-fn  tenting-angle)
         (rotate-x-fn  rotate-x-angle)
         (translate-fn [0 0 keyboard-z-offset]))))

(defn rotate-around-x [angle position]
  (mmul
   [[1 0 0]
    [0 (Math/cos angle) (- (Math/sin angle))]
    [0 (Math/sin angle)    (Math/cos angle)]]
   position))

(defn rotate-around-y [angle position]
  (mmul
   [[(Math/cos angle)     0 (Math/sin angle)]
    [0                    1 0]
    [(- (Math/sin angle)) 0 (Math/cos angle)]]
   position))

(def left-wall-x-offset 10)
(def left-wall-z-offset  3)

(defn key-position
  "determines the position of a key based on the
  configuration, column, row, and position.
  it takes configuration to determine whether it is the last column
  and some other options like whether it's a part of a staggered board
  or not."
  [c column row position]
  (apply-key-geometry c
                      (partial map +)
                      rotate-around-x
                      rotate-around-y
                      column
                      row
                      position))

(defn left-key-position
  "determines the position of the left column key position."
  [c row direction]
  (map -
       (key-position c 0 row [(* mount-width -0.5) (* direction mount-height 0.5) 0])
       [left-wall-x-offset 0 left-wall-z-offset]))

(defn index-key-position
  "determines the position of the left column key position."
  [c row direction]
  (map -
       (key-position c 1 row [(* mount-width -0.5) (* direction mount-height 0.5) 0])
       [left-wall-x-offset 0 left-wall-z-offset]))

;;;;;;;;;;;;;;;;;
;; Switch Hole ;;
;;;;;;;;;;;;;;;;;
(defn single-plate
  "Defines the form of switch hole. It determines the whether it uses
   box or mx style based on the `configuration-create-side-nub?`. It also
   asks whether it creates hotswap housing or not based on `configuration-use-hotswap?`.
   and determines whether it should use alps cutout or not based on  `configuration-use-alps?`"
  [c]
  (let [switch-type         (get c :configuration-switch-type)
        create-side-nub?    (case switch-type
                              :mx true
                              :mx-snap-in true
                              false) #_(get c :configuration-create-side-nub?)
        nub-height           (case switch-type
                               :mx-snap-in 0.75
                               0)
        use-alps?           (case switch-type
                              :alps true
                              false) #_(get c :configuration-use-alps?)
        use-choc?           (case switch-type
                              :choc true
                              false)
        use-hotswap?        (get c :configuration-use-hotswap?)
        is-right?           (get c :is-right?)
        plate-projection?   (get c :configuration-plate-projection? false)
        fill-in             (translate [0 0 (/ plate-thickness 2)] (cube alps-width alps-height plate-thickness))
        holder-thickness    1.65
        top-wall            (case switch-type
                              :alps (->> (cube (+ keyswitch-width 3) 2.7 plate-thickness)
                                         (translate [0
                                                     (+ (/ 2.7 2) (/ alps-height 2))
                                                     (/ plate-thickness 2)]))
                              :choc (->> (cube (+ keyswitch-width 3) holder-thickness (* plate-thickness 0.65))
                                         (translate [0
                                                     (+ holder-thickness (/ keyswitch-height 2))
                                                     (* plate-thickness 0.7)]))
                              (->> (cube (+ keyswitch-width 3.3) holder-thickness plate-thickness)
                                   (translate [0
                                               (+ (/ holder-thickness 2) (/ keyswitch-height 2))
                                               (/ plate-thickness 2)])))
        left-wall           (case switch-type
                              :alps (union (->> (cube 2 (+ keyswitch-height 3) plate-thickness)
                                                (translate [(+ (/ 2 2) (/ 15.6 2))
                                                            0
                                                            (/ plate-thickness 2)]))
                                           (->> (cube 1.5 (+ keyswitch-height 3) 1.0)
                                                (translate [(+ (/ 1.5 2) (/ alps-notch-width 2))
                                                            0
                                                            (- plate-thickness
                                                               (/ alps-notch-height 2))])))
                              :choc (->> (cube holder-thickness (+ keyswitch-height 3.3) (* plate-thickness 0.65))
                                         (translate [(+ (/ holder-thickness 2) (/ keyswitch-width 2))
                                                     0
                                                     (* plate-thickness 0.7)]))
                              (->> (cube holder-thickness (+ keyswitch-height 3.3) plate-thickness)
                                   (translate [(+ (/ holder-thickness 2) (/ keyswitch-width 2))
                                               0
                                               (/ plate-thickness 2)])))
        side-nub            (->> (binding [*fn* 30] (cylinder 1 2.75))
                                 (rotate (/ pi 2) [1 0 0])
                                 (translate [(+ (/ keyswitch-width 2)) 0 (+ 1 nub-height)])
                                 (hull (->> (cube 1.5 2.75 (- plate-thickness nub-height))
                                            (translate [(+ (/ 1.5 2) (/ keyswitch-width 2))
                                                        0
                                                        (/ (+ plate-thickness nub-height) 2)]))))
        ; the hole's wall.
        kailh-cutout (->> (cube (/ keyswitch-width 3) 1.6 (+ plate-thickness 1.8))
                          (translate [0
                                      (+ (/ 1.5 2) (+ (/ keyswitch-height 2)))
                                      (/ plate-thickness)]))
        plate-half          (case switch-type
                              :kailh (union (difference top-wall kailh-cutout) left-wall)
                              (union top-wall
                                     left-wall
                                     (if create-side-nub? (with-fn 100 side-nub))))
        ; the bottom of the hole.
        swap-holder-z-offset (if use-choc? 1.5 -1.5)
        swap-holder         (->> (cube (+ keyswitch-width 3) (/ keyswitch-height 2) 3)
                                 (translate [0 (/ (+ keyswitch-height 3) 4) swap-holder-z-offset]))
        ; for the main axis
        main-axis-hole      (->> (cylinder (/ 4.0 2) 10)
                                 (with-fn 12))
        plus-hole           (->> (cylinder (/ 3.3 2) 10)
                                 (with-fn 8)
                                 (translate (if use-choc? [-5 4 0] [-3.81 2.54 0])))
        minus-hole          (->> (cylinder (/ 3.3 2) 10)
                                 (with-fn 8)
                                 (translate (if use-choc? [0 6 5] [2.54 5.08 0])))
        plus-hole-mirrored  (->> (cylinder (/ 3.3 2) 10)
                                 (with-fn 8)
                                 (translate (if use-choc? [5 4 5] [3.81 2.54 0])))
        minus-hole-mirrored (->> (cylinder (/ 3.3 2) 10)
                                 (with-fn 8)
                                 (translate (if use-choc? [0 6 5] [-2.54 5.08 0])))
        friction-hole       (->> (cylinder (if use-choc? 1 (/ 1.7 2)) 10)
                                 (with-fn 8))
        friction-hole-right (translate [(if use-choc? 5.5 5) 0 0] friction-hole)
        friction-hole-left  (translate [(if use-choc? -5.5 -5) 0 0] friction-hole)
        hotswap-base-z-offset (if use-choc? 0.2 -2.6)
        hotswap-base-shape  (->> (cube 19 (if use-choc? 11.5 8.2) 3.5)
                                 (translate [0 5 hotswap-base-z-offset]))
        choc-socket-holder-height 5.5
        choc-socket-holder-thickness 1
        choc-hotswap-socket-holder (difference
                                    (->> (cube 10 7 choc-socket-holder-height)
                                         (translate [2 5 hotswap-base-z-offset]))
                                    (->> (cube 5 7 choc-socket-holder-height)
                                         (translate [-0.6 6 (+ hotswap-base-z-offset choc-socket-holder-thickness)]))
                                    (->> (cube 7 7 choc-socket-holder-height)
                                         (translate [5 4 (+ hotswap-base-z-offset choc-socket-holder-thickness)])))
        hotswap-holder      (union (if use-choc? choc-hotswap-socket-holder ())
                                   (difference swap-holder
                                               main-axis-hole
                                               (union plus-hole plus-hole-mirrored)
                                               (union minus-hole minus-hole-mirrored)
                                               friction-hole-left
                                               friction-hole-right
                                               hotswap-base-shape))]
    (difference (union plate-half
                       (->> plate-half
                            (mirror [1 0 0])
                            (mirror [0 1 0]))
                       (if plate-projection? fill-in ())
                       (if (and use-hotswap?
                                (not use-alps?))
                         hotswap-holder
                         ())))))
;;;;;;;;;;;;;;;;
;; SA Keycaps ;;
;;;;;;;;;;;;;;;;

(def sa-length 18.25)
(def sa-double-length 37.5)
(def sa-cap
  {1   (let [bl2     (/ 18.5 2)
             m       (/ 17 2)
             key-cap (hull (->> (polygon [[bl2 bl2] [bl2 (- bl2)] [(- bl2) (- bl2)] [(- bl2) bl2]])
                                (extrude-linear {:height    0.1
                                                 :twist     0
                                                 :convexity 0})
                                (translate [0 0 0.05]))
                           (->> (polygon [[m m] [m (- m)] [(- m) (- m)] [(- m) m]])
                                (extrude-linear {:height    0.1
                                                 :twist     0
                                                 :convexity 0})
                                (translate [0 0 6]))
                           (->> (polygon [[6 6] [6 -6] [-6 -6] [-6 6]])
                                (extrude-linear {:height    0.1
                                                 :twist     0
                                                 :convexity 0})
                                (translate [0 0 12])))]
         (->> key-cap
              (translate [0 0 (+ 5 plate-thickness)])
              (color [220/255 163/255 163/255 1])))
   2   (let [bl2     (/ sa-double-length 2)
             bw2     (/ 18.25 2)
             key-cap (hull (->> (polygon [[bw2 bl2] [bw2 (- bl2)] [(- bw2) (- bl2)] [(- bw2) bl2]])
                                (extrude-linear {:height    0.1
                                                 :twist     0
                                                 :convexity 0})
                                (translate [0 0 0.05]))
                           (->> (polygon [[6 16] [6 -16] [-6 -16] [-6 16]])
                                (extrude-linear {:height    0.1
                                                 :twist     0
                                                 :convexity 0})
                                (translate [0 0 12])))]
         (->> key-cap
              (translate [0 0 (+ 5 plate-thickness)])
              (color [127/255 159/255 127/255 1])))
   1.5 (let [bl2     (/ 18.25 2)
             bw2     (/ 28 2)
             key-cap (hull (->> (polygon [[bw2 bl2] [bw2 (- bl2)] [(- bw2) (- bl2)] [(- bw2) bl2]])
                                (extrude-linear {:height    0.1
                                                 :twist     0
                                                 :convexity 0})
                                (translate [0 0 0.05]))
                           (->> (polygon [[11 6] [-11 6] [-11 -6] [11 -6]])
                                (extrude-linear {:height    0.1
                                                 :twist     0
                                                 :convexity 0})
                                (translate [0 0 12])))]
         (->> key-cap
              (translate [0 0 (+ 5 plate-thickness)])
              (color [240/255 223/255 175/255 1])))})

(def web-thickness 7)
(def post-size 0.1)
;; TODO remove the constants once lightcycle has been converted
(def web-post
  (->> (cube post-size post-size web-thickness)
       (translate [0 0 (+ (/ web-thickness -2)
                          plate-thickness)])))
(defn web-post [web-thickness]
  (->> (cube post-size post-size web-thickness)
       (translate [0 0 (+ (/ web-thickness -2)
                          plate-thickness)])))

(def post-adj (/ post-size 2))

;; TODO remove the constants once lightcycle has been converted
;; (def web-post-tr (translate [(- (/ mount-width 2) post-adj) (- (/ mount-height 2) post-adj) 0] web-post))
;; (def web-post-tl (translate [(+ (/ mount-width -2) post-adj) (- (/ mount-height 2) post-adj) 0] web-post))
;; (def web-post-bl (translate [(+ (/ mount-width -2) post-adj) (+ (/ mount-height -2) post-adj) 0] web-post))
;; (def web-post-br (translate [(- (/ mount-width 2) post-adj) (+ (/ mount-height -2) post-adj) 0] web-post))
(defn web-post-tr [web-thickness] (translate [(- (/ mount-width 2) post-adj) (- (/ mount-height 2) post-adj) 0] (web-post web-thickness)))
(defn web-post-tl [web-thickness] (translate [(+ (/ mount-width -2) post-adj) (- (/ mount-height 2) post-adj) 0] (web-post web-thickness)))
(defn web-post-bl [web-thickness] (translate [(+ (/ mount-width -2) post-adj) (+ (/ mount-height -2) post-adj) 0] (web-post web-thickness)))
(defn web-post-br [web-thickness] (translate [(- (/ mount-width 2) post-adj) (+ (/ mount-height -2) post-adj) 0] (web-post web-thickness)))

; length of the first downward-sloping part of the wall (negative)
(def wall-z-offset -15)
; offset in the x and/or y direction for the first downward-sloping part of the wall (negative)
(def wall-xy-offset 5)
; wall thickness parameter; originally 5
(def wall-thickness 3)

;; TODO remove those functions once lightcycle has been integrated
(defn wall-locate1 [dx dy]
  [(* dx wall-thickness) (* dy wall-thickness) -1])
(defn wall-locate2 [dx dy]
  [(* dx wall-xy-offset) (* dy wall-xy-offset) wall-z-offset])
(defn wall-locate3 [dx dy]
  [(* dx (+ wall-xy-offset wall-thickness))
   (* dy (+ wall-xy-offset wall-thickness))
   wall-z-offset])

(defn wall-locate1 [wall-thickness dx dy]
  [(* dx wall-thickness) (* dy wall-thickness) -1])
(defn wall-locate2 [wall-thickness dx dy]
  [(* dx wall-xy-offset) (* dy wall-xy-offset) wall-z-offset])
(defn wall-locate3 [wall-thickness dx dy]
  [(* dx (+ wall-xy-offset wall-thickness))
   (* dy (+ wall-xy-offset wall-thickness))
   wall-z-offset])
;; connectors
; (def rj9-cube (cube 14.78 13 22.38))
;; Measured size is (def rj9-cube (cube 12.75 14.05 17)). But adding some slack
(def rj9-hole-wall-thickness 1.5)
(def rj9-hole-wall-modifier (* rj9-hole-wall-thickness 2)) ; times 2 because it's on both sides of each dimension
(def rj9-hole [13.90 12.75 22])
(def rj9-hole-cube (cube (first rj9-hole) (second rj9-hole) (last rj9-hole)))
(def rj9-wire-hole [(first rj9-hole) (second rj9-hole) (/ (last rj9-hole) 2.6)])
(def rj9-cube (cube (+ (first rj9-hole) rj9-hole-wall-modifier) (+ (second rj9-hole) rj9-hole-wall-modifier) (+ (last rj9-hole) rj9-hole-wall-modifier)))
(defn rj9-position
  "determines the position of the rj9 housing.
  it takes a function that generates the coordinate of the housing
  and the configuration."
  [frj9-start c]
  [(first (frj9-start c)) (second (frj9-start c)) (+ 11 rj9-hole-wall-thickness)])
(defn rj9-space
  "puts the space of the rj9 housing based on function and configuration
  that is provided."
  [frj9-start c]
  (translate (rj9-position frj9-start c) rj9-cube))
(defn rj9-holder
  "TODO: doc"
  [frj9-start c]
  (translate
   (rj9-position frj9-start c)
   (difference rj9-cube
               (union
                (cube (first rj9-hole) (second rj9-hole) (last rj9-hole))
                (translate [0 (+ rj9-hole-wall-thickness 0) 0] rj9-hole-cube)
                (translate [0 (- 0 rj9-hole-wall-thickness) (- (/ (last rj9-hole) 2) (/ (last rj9-wire-hole) 2))]
                           (cube (first rj9-wire-hole) (second rj9-wire-hole) (last rj9-wire-hole)))))))

; (def usb-holder-size [12.5 13.0 17.6])
(def usb-holder-size [12.5 13.0 8.0])
(def usb-holder-thickness 2)
(def usb-holder-offset [-17 2.4 0])
(defn usb-holder
  "TODO: doc"
  [fusb-holder-position c]
  (->> (cube (+ (first usb-holder-size) usb-holder-thickness)
             (second usb-holder-size)
             (+ (last usb-holder-size) usb-holder-thickness))
       (translate [(+ (first (fusb-holder-position c)) (first usb-holder-offset))
                   (+ (second (fusb-holder-position c)) (second usb-holder-offset))
                   (+ (/ (+ (last usb-holder-size) usb-holder-thickness) 2) (last usb-holder-offset))])))
(defn usb-holder-hole
  "TODO: doc"
  [fusb-holder-position c]
  (->> (apply cube usb-holder-size)
       (translate [(+ (first (fusb-holder-position c)) (first usb-holder-offset))
                   (+ (second (fusb-holder-position c)) (second usb-holder-offset))
                   (+ (/ (+ (last usb-holder-size) usb-holder-thickness) 2) (last usb-holder-offset))])))

(defn screw-insert-shape [bottom-radius top-radius height]
  (union (cylinder [bottom-radius top-radius] height)
         (translate [0 0 (/ height 2)] (sphere top-radius))))

(def screw-insert-height 6)
(def screw-insert-bottom-radius (/ 5.55 2))
(def screw-insert-top-radius (/ 5.55 2))
; (def screw-insert-height 3.8)
; (def screw-insert-bottom-radius (/ 5.31 2))
; (def screw-insert-top-radius (/ 5.1 2))

(defn screw-insert-holes
  "TODO: doc.
   but basically it takes a function that places screw holes with the following specs."
  [placement-function c]
  (placement-function c
                      screw-insert-bottom-radius
                      screw-insert-top-radius
                      screw-insert-height :inner-hole))
(defn screw-insert-outers
  "TODO: doc.
   but basically it takes a function that places outer parts of screw holes with the following specs."
  [placement-function c]
  (placement-function c
                      (+ screw-insert-bottom-radius 1.6)
                      (+ screw-insert-top-radius 1.6)
                      (+ screw-insert-height 1.5)
                      :outer-hole))

(defn screw-insert-screw-holes
  "TODO: doc."
  [placement-function c thickness]
  (placement-function c 2 3.1 thickness :plate-hole))

(def m3-hex-screw-slit-length 5.9) ; height of an M3 hex bolt hexagon == 2*apothem

(defn hexagon-side-length [apothem]
  (* 2 apothem (Math/tan (/ pi 6))))

(defn hex-screw-slit
  [hex-apothem height]
  (let [side (hexagon-side-length hex-apothem)
        part (fn [angle]
               (translate [0 0 (/ height 2)]
                          (rotate angle [0 0 1]
                                  (cube side (* hex-apothem 2) height :center true))))]
    (union
     (part 0)
     (part (/ pi 3))
     (part (* 2 (/ pi 3))))))

(defn hex-bolt-hole-shape
  "add a screw hole that holds an M3 hex bolt"
  [bottom-radius top-radius height]
  (hull
   (hex-screw-slit (/ m3-hex-screw-slit-length 2) height)

   (translate [0 0 height]
              (sphere (hexagon-side-length (/ m3-hex-screw-slit-length 2))))))
   ; (translate [0 0 height]
   ;            (difference
   ;             (difference
   ;              (sphere top-radius)
   ;              (sphere (- top-radius 0.5)))
   ;             (rotate (/ pi 2) [1 0 0]
   ;                     (translate [(- top-radius), (- top-radius), 0] (cube (* 2 top-radius) (* 2 top-radius) (* 2 top-radius))))))))

(defn hex-bolt-case-shape
  "add a bolt hole enclosure"
  [bottom-radius top-radius height]
  (union
   (cylinder [bottom-radius, top-radius] height :center false)

   (translate [0 0 height]
              (sphere top-radius))))

(defn screw-bolt-holder [bottom-radius top-radius height]
  (binding [*fn* 30] (union
                      (cylinder bottom-radius height :center false)
                      (cylinder top-radius (- height 1) :center false))))

(defn screw-insert [c column row bottom-radius top-radius height type]
  (let [lastcol     (flastcol (get c :configuration-ncols))
        lastrow     (flastrow (get c :configuration-nrows 5))
        shift-right (= column lastcol)
        shift-left  (= column 0)
        shift-up    (and (not (or shift-right shift-left)) (= row 0))
        shift-down  (and (not (or shift-right shift-left)) (>= row lastrow))
        wall-thickness (get c :configuration-wall-thickness 5)
        position    (if shift-up
                      (key-position c column row (map + (wall-locate2 wall-thickness 0  1) [0 (/ mount-height 2) 0]))
                      (if shift-down
                        (key-position c column row (map - (wall-locate2 wall-thickness 0 -1) [0 (/ mount-height 2) 0]))
                        (if shift-left
                          (map + (left-key-position c row 0) (wall-locate3 wall-thickness -1 0))
                          (key-position c column row (map + (wall-locate2 wall-thickness 1  0) [(/ mount-width 2) 0 0])))))]
    (case type
      :outer-hole (->> (hex-bolt-case-shape bottom-radius top-radius height)
                       (translate [(first position) (second position) 0]))
      :inner-hole (->> (hex-bolt-hole-shape bottom-radius top-radius height)
                       (translate [(first position) (second position) 0]))
      :plate-hole (->> (screw-bolt-holder bottom-radius top-radius height)
                       (translate [(first position) (second position) 0])))))
; (->> (screw-insert-shape bottom-radius top-radius height)
    ;      (translate [(first position) (second position) (/ height 2)]))))

;;;;;;;;;;;;;;;;;;;;;;
;; Raspberry pi holder
;;;;;;;;;;;;;;;;;;;;;;

(def pi-holder-inner-screw 2.7)
(def pi-holder-outer-screw 4)

(def pi-holder-x-hole-distance 11.2)
(def pi-holder-y-hole-distance 46.85)

(defn pi-holder-screw-hole [x, y, z, thickness]
  (translate [x y z] (union
                      (extrude-linear {:height thickness :center false}
                                      (circle (/ pi-holder-inner-screw 2)))
                      (extrude-linear {:height (- thickness 1) :center false}
                                      (circle (/ pi-holder-outer-screw 2))))))

; (def pi-holder-reset-x 3)
; (def pi-holder-reset-y 38.4)
(def pi-holder-reset-x 2)
(def pi-holder-reset-y 37.3)
(defn pi-holder-reset-hole [thickness]
  (cylinder (/ pi-holder-inner-screw 2) thickness :center false))

(def plate-screw-hole-offset 4.5)

(defn pi-holder-holes [plate-thickness side]
  (binding [*fn* 30]
    (union
     (pi-holder-screw-hole pi-holder-x-hole-distance 0 0 plate-thickness)
     (pi-holder-screw-hole pi-holder-x-hole-distance pi-holder-y-hole-distance 0 plate-thickness)
     (pi-holder-screw-hole 0 pi-holder-y-hole-distance 0 plate-thickness)
     (pi-holder-screw-hole 0 0 0 plate-thickness)
     (->> (pi-holder-reset-hole plate-thickness)
          (translate [(case side
                        :left pi-holder-reset-x
                        :right (- pi-holder-x-hole-distance pi-holder-reset-x)),
                      pi-holder-reset-y,
                      0])))))
(defn pi-holder-holder-screw-holes [hole]
  (union
   (translate [(+ (/ pi-holder-x-hole-distance 2) plate-screw-hole-offset) (/ pi-holder-y-hole-distance 2) 0]
              hole)
   (translate [(- (/ pi-holder-x-hole-distance 2) plate-screw-hole-offset) (/ pi-holder-y-hole-distance 2) 0]
              hole)
   (translate [(/ pi-holder-x-hole-distance 2) (- (/ pi-holder-y-hole-distance 2) (* plate-screw-hole-offset 2)) 0]
              hole)))

(defn pi-holder [c, thickness]
  (let [plate-screw-hole (union
                          (hex-screw-slit (/ m3-hex-screw-slit-length 2) (- thickness 0.4))
                          (hex-screw-slit 2 thickness))]
    (difference
     (translate [(- pi-holder-outer-screw) (- pi-holder-outer-screw) 0]
                (cube (+ pi-holder-x-hole-distance (* 2 pi-holder-outer-screw))
                      (+ pi-holder-y-hole-distance (* 2 pi-holder-outer-screw))
                      thickness
                      :center false))
     (pi-holder-holder-screw-holes plate-screw-hole)
     (pi-holder-holes thickness :right))))

(defn pi-holder-holder [plate-thickness side]
  (binding [*fn* 30]
    (let [hole
          (screw-insert-screw-holes (fn [_ bottom-radius top-radius height _] (screw-bolt-holder bottom-radius top-radius height)) 0 plate-thickness)]
      (union
       (pi-holder-holder-screw-holes hole)
       (->> (pi-holder-reset-hole plate-thickness)
            (translate [(case side
                          :left pi-holder-reset-x
                          :right (- pi-holder-x-hole-distance pi-holder-reset-x)),
                        pi-holder-reset-y,
                        0]))))))
