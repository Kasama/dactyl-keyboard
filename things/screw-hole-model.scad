$fn=30;

module slit(angle) {
    rotate(a = angle, v = [0,0,1]) { 
        cube (size = [3.464,6,20], center = true);
    };
}

bolt_depth = 5;

module screw_hole(bolt_depth, radius) {
    union() {
        difference () {
            union() {
                cylinder (h=bolt_depth, r1=radius, r2=radius, center=false);
            }
            union () {
              slit(0.0);
              slit(60.0);
              slit(120.0);
            }
        }
        translate([0,0,bolt_depth]) {
            difference() {
                difference() {
                    sphere(r = radius);
                    sphere(r = radius - 0.5);
                }
                rotate(a = 180, v = [1,0,0]){
                    translate([-100, -100, 0]){
                        cube(200);
                    }
                }
            }
        }
    }
}

module hexslit(height, angle) {
    xy = 5.8;
    side = xy * (tan(30));
    translate ([0, 0, height/2]) {
        rotate(a = angle, v = [0,0,1]) { 
            cube (size = [side,xy,height], center = true);
        }
    }
}

module hex_bolt(height, radius) {
    hull() {
        union() {
            hexslit(height, 0);
            hexslit(height, 60);
            hexslit(height, 120);
        }
        union() {
            translate([0,0,height]){
                sphere(r=(5.8 * tan(30)));
            }
        }
    }
}

difference() {
    hex_bolt(5, 5);
}
