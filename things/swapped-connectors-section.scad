intersection() {
    difference () {
        // main
        translate([-110,9,0]) {
            cube([62,45,25.5]);
        };
        // rj9 - right cut
        translate([-53.8,0,0]) {
            rotate (a=0, v=[0,0,1]) {
                cube([18,50,30]);
            }
        };
        // back cut
        translate([-63,0,11]) {
            rotate (a=0, v=[0,0,1]) {
                cube([18,26,30]);
            }
        };
        // usb - left cut
        translate([-108,9,11]) {
            rotate (a=0, v=[0,0,1]) {
                cube([35,78,19]);
            }
        };
    }
}