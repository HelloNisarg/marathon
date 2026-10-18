package app.stride.seven;
public class TrackingMathTest {
    static void check(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args){
        check(TrackingMath.stepDelta(-1,1200,true)==0); // no historical steps on enable
        check(TrackingMath.stepDelta(1200,1215,true)==15);
        check(TrackingMath.stepDelta(1200,4,true)==0); // reboot/reset
        check(TrackingMath.stepDelta(1200,1215,false)==0); // no misassignment across midnight
        check(TrackingMath.validLeg(10,4));
        check(!TrackingMath.validLeg(100,2)); // GPS jump
        check(!TrackingMath.validLeg(10,31)); // signal gap
        check(!TrackingMath.validLeg(1,2)); // stationary jitter
        check(!TrackingMath.validLeg(10,-1));
        check(!TrackingMath.validLeg(Double.NaN,2));
        check(!TrackingMath.reached(6999,7000));check(TrackingMath.reached(7000,7000));check(TrackingMath.reached(7002,7000));check(!TrackingMath.reached(0,0));
        System.out.println("14 tracking accounting checks passed");
    }
}
