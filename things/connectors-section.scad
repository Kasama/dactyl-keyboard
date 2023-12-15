intersection() {
    difference () {
        // main
        translate([-110,9,0]) {
            cube([62,45,25.5]);
        };
        // usb - right cut
        translate([-73,9,11]) {
            rotate (a=0, v=[0,0,1]) {
                cube([25,78,19]);
            }
        };
        // rj9 - left cut
        translate([-110,9,11]) {
            rotate (a=0, v=[0,0,1]) {
                cube([18,28,19]);
            }
        };
    }
}