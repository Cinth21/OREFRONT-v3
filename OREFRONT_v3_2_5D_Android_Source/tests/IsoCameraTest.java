import com.student.orefront.IsoCamera;
public final class IsoCameraTest {
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    static void near(float a,float b,String text){check(Math.abs(a-b)<.001f,text+": "+a+" vs "+b);}
    public static void main(String[] args){
        IsoCamera cam=new IsoCamera();
        for(int x=0;x<18;x++)for(int y=0;y<12;y++){
            int[] hit=cam.tileAt(IsoCamera.worldX(x,y),IsoCamera.worldY(x,y));
            check(hit[0]==x&&hit[1]==y,"tile hit "+x+","+y);
        }
        cam.zoomAt(350,500,1.7f);
        for(int x=0;x<18;x++)for(int y=0;y<12;y++){
            float px=cam.screenX(IsoCamera.worldX(x,y)),py=cam.screenY(IsoCamera.worldY(x,y));
            int[] hit=cam.tileAt(px,py);
            check(hit[0]==x&&hit[1]==y,"zoom hit "+x+","+y);
        }
        float originalX=cam.unprojectX(430),originalY=cam.unprojectY(480);
        cam.zoomAt(430,480,2.1f);
        near(originalX,cam.unprojectX(430),"zoom anchor x");near(originalY,cam.unprojectY(480),"zoom anchor y");
        cam.move(65,-40);
        int[] hit=cam.tileAt(cam.screenX(IsoCamera.worldX(5,6)),cam.screenY(IsoCamera.worldY(5,6)));
        check(hit[0]==5&&hit[1]==6,"dragged camera hit");
        cam.zoomAt(400,500,100f);near(cam.zoom(),IsoCamera.MAX_ZOOM,"max zoom");
        cam.zoomAt(400,500,-100f);near(cam.zoom(),IsoCamera.MIN_ZOOM,"min zoom");
        cam.reset();near(cam.zoom(),1f,"reset zoom");near(cam.panX(),0f,"reset pan x");near(cam.panY(),0f,"reset pan y");
        System.out.println("PASS: 216 tile inverse projections, zoom anchor, drag, clamp, reset");
    }
}
