package com.student.orefront;

/** Camera transform for the OREFRONT 18x12 isometric battlefield.
 * Pure Java so its touch/zoom mathematics can be unit-tested without Android.
 */
public final class IsoCamera {
    public static final float ORIGIN_X=310f, ORIGIN_Y=244f;
    public static final float TILE_W=40f, TILE_H=30f;
    public static final float VIEW_CENTER_X=360f, VIEW_CENTER_Y=490f;
    public static final float MIN_ZOOM=0.75f, MAX_ZOOM=2.25f;
    private float zoom=1f, panX=0f, panY=0f;

    public float zoom(){return zoom;}
    public float panX(){return panX;}
    public float panY(){return panY;}
    public void reset(){zoom=1f;panX=0f;panY=0f;}
    public static float worldX(float tileX,float tileY){return ORIGIN_X+(tileX-tileY)*TILE_W/2f;}
    public static float worldY(float tileX,float tileY){return ORIGIN_Y+(tileX+tileY)*TILE_H/2f;}
    public float screenX(float worldX){return VIEW_CENTER_X+(worldX-VIEW_CENTER_X)*zoom+panX;}
    public float screenY(float worldY){return VIEW_CENTER_Y+(worldY-VIEW_CENTER_Y)*zoom+panY;}
    public float unprojectX(float screenX){return VIEW_CENTER_X+(screenX-VIEW_CENTER_X-panX)/zoom;}
    public float unprojectY(float screenY){return VIEW_CENTER_Y+(screenY-VIEW_CENTER_Y-panY)/zoom;}
    public float tileX(float sx,float sy){float a=(unprojectX(sx)-ORIGIN_X)/(TILE_W/2f);
        float b=(unprojectY(sy)-ORIGIN_Y)/(TILE_H/2f);return (a+b)/2f;}
    public float tileY(float sx,float sy){float a=(unprojectX(sx)-ORIGIN_X)/(TILE_W/2f);
        float b=(unprojectY(sy)-ORIGIN_Y)/(TILE_H/2f);return (b-a)/2f;}
    public int[] tileAt(float sx,float sy){return new int[]{Math.round(tileX(sx,sy)),Math.round(tileY(sx,sy))};}
    public void move(float dx,float dy){panX=limit(panX+dx,-620f,620f);panY=limit(panY+dy,-590f,590f);}
    /** Zoom keeping the same underlying battlefield position under the finger. */
    public void zoomAt(float sx,float sy,float requested){
        float wx=unprojectX(sx),wy=unprojectY(sy);
        zoom=limit(requested,MIN_ZOOM,MAX_ZOOM);
        panX=limit(sx-VIEW_CENTER_X-(wx-VIEW_CENTER_X)*zoom,-620f,620f);
        panY=limit(sy-VIEW_CENTER_Y-(wy-VIEW_CENTER_Y)*zoom,-590f,590f);
    }
    public void focusOn(float tileX,float tileY){
        panX=limit((VIEW_CENTER_X-worldX(tileX,tileY))*zoom,-620f,620f);
        panY=limit((VIEW_CENTER_Y-worldY(tileX,tileY))*zoom,-590f,590f);
    }
    private static float limit(float n,float low,float high){return Math.max(low,Math.min(high,n));}
}
