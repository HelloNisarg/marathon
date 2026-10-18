package app.stride.seven;
/** Pure accounting rules shared by the tracker and host tests. */
final class TrackingMath {
    static long stepDelta(long previous,long current,boolean sameDay){return sameDay&&previous>=0&&current>=previous?current-previous:0;}
    static boolean validLeg(double meters,double seconds){return Double.isFinite(meters)&&Double.isFinite(seconds)&&meters>=3&&seconds>0&&seconds<=30&&meters/seconds<12;}
    static boolean reached(double value,double target){return Double.isFinite(value)&&Double.isFinite(target)&&target>0&&value>=target;}
}
