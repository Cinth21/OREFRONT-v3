package com.student.orefront;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.*;
import java.util.concurrent.*;

/** OREFRONT 3.0: enhanced 2.5D tactical RTS with on-device AI and real phone-hosted LAN. */
public final class MainActivity extends Activity implements LanRoom.Listener {
    private enum Page { HOME, LOBBY, BATTLE }
    private Page page=Page.HOME;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService offlineClock=Executors.newSingleThreadScheduledExecutor();
    private GameView view;
    private volatile GameCore state;
    private LanRoom.Server server;
    private LanRoom.Client client;
    private boolean offline=false,connected=false;
    private String room="",hostIP="",info="",intel="",username="Commander";
    private String token=UUID.randomUUID().toString();
    private int myId=0,selectedPlayer=0,selectedUnit=-1,selectedBuilding=-1,tab=0;
    private String placement="";

    @Override public void onCreate(Bundle b){super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        username=getPreferences(0).getString("name","Commander");
        token=getPreferences(0).getString("token",UUID.randomUUID().toString());
        getPreferences(0).edit().putString("token",token).apply();
        view=new GameView(this);setContentView(view);
        offlineClock.scheduleAtFixedRate(()->{
            if(!offline||state==null||!state.phase.equals("PLAY"))return;
            ui.post(()->{if(offline&&state!=null&&state.phase.equals("PLAY")){state.tick();view.invalidate();}});
        },500,500,TimeUnit.MILLISECONDS);
    }
    @Override protected void onDestroy(){leaveRoom();offlineClock.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(page==Page.HOME)super.onBackPressed();else new AlertDialog.Builder(this)
        .setTitle("Leave OREFRONT?").setMessage("The host's match will end if the host leaves.")
        .setNegativeButton("Cancel",null).setPositiveButton("Leave",(d,w)->leaveRoom()).show();}
    private void toast(String text){ui.post(()->{info=text;Toast.makeText(this,text,Toast.LENGTH_SHORT).show();view.invalidate();});}
    private void refresh(GameCore w,int id){ui.post(()->{state=w;myId=id;connected=true;
        page=w.phase.equals("LOBBY")?Page.LOBBY:Page.BATTLE;view.invalidate();});}
    @Override public void onState(GameCore w,int id){refresh(w,id);}
    @Override public void onMessage(String msg){ui.post(()->{if(msg.startsWith("INTEL"))intel=msg;else info=msg;view.invalidate();});}
    @Override public void onDisconnected(String msg){ui.post(()->{connected=false;info=msg;view.invalidate();
        new AlertDialog.Builder(this).setTitle("Connection lost").setMessage(msg+"\nYour faction remains at the host. Use Join Room with the same phone to reconnect.")
        .setPositiveButton("OK",null).show();});}
    private void leaveRoom(){
        if(client!=null){client.command("LEAVE");client.close();client=null;}
        if(server!=null){server.close();server=null;}
        offline=false;connected=false;page=Page.HOME;room="";hostIP="";state=null;
        placement="";selectedUnit=-1;selectedBuilding=-1;tab=0;info="";intel="";
        if(view!=null){view.camera.reset();view.visualPositions.clear();view.invalidate();}
    }
    private void chooseName(Runnable next){EditText input=new EditText(this);input.setSingleLine(true);input.setText(username);input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Commander username").setView(input).setNegativeButton("Cancel",null)
            .setPositiveButton("Continue",(d,w)->{username=GameCore.clean(input.getText().toString());
                getPreferences(0).edit().putString("name",username).apply();next.run();}).show();}
    private void startAI(){new AlertDialog.Builder(this).setTitle("Choose opponent intelligence")
        .setItems(new String[]{"EASY • 2 enemy factions","MEDIUM • 3 enemy factions","HARD • 5 enemy factions"},(d,which)->{
            leaveRoom();offline=true;connected=true;state=new GameCore();state.addOfflineAI(which==0?2:which==1?3:5,which);
            page=Page.BATTLE;myId=0;info="AI factions use economy, combat priorities and BFS routing.";view.invalidate();
        }).show();}
    private void host(){leaveRoom();new Thread(()->{try{
        LanRoom.Server s=new LanRoom.Server(username,token,this);
        ui.post(()->{server=s;room=s.code();hostIP=s.ip();connected=true;page=Page.LOBBY;
            info="Local room hosted on this Android phone";state=s.snapshot();view.invalidate();});
    }catch(Exception ex){toast("Host failed: "+ex.getMessage());}},"orefront-host-init").start();}
    private void manualJoin(){
        LinearLayout form=new LinearLayout(this);form.setPadding(32,18,32,6);form.setOrientation(LinearLayout.VERTICAL);
        TextView hint=new TextView(this);hint.setText("Use the same Wi-Fi or hotspot. Room code is required. Host IP is optional if discovery works.");form.addView(hint);
        EditText code=new EditText(this);code.setHint("6-digit room code");code.setInputType(InputType.TYPE_CLASS_NUMBER);form.addView(code);
        EditText ip=new EditText(this);ip.setHint("Host IP (optional)");ip.setSingleLine(true);form.addView(ip);
        new AlertDialog.Builder(this).setTitle("JOIN LOCAL LAN ROOM").setView(form).setNegativeButton("Cancel",null)
            .setPositiveButton("Join",(d,w)->{String c=code.getText().toString().trim(),addr=ip.getText().toString().trim();
                if(!c.matches("[0-9]{6}")){toast("Room code must contain six digits");return;}join(c,addr);}).show();
    }
    private void join(String c,String address){leaveRoom();info="Searching for the host phone...";view.invalidate();
        new Thread(()->{String ip=address;try{
            if(ip.isEmpty()){for(LanRoom.RoomInfo r:LanRoom.search(2300))if(r.code.equals(c)){ip=r.host;break;}
                if(ip.isEmpty())throw new IllegalStateException("Room not found. Enter the host IP displayed on its lobby.");}
            final String dest=ip;LanRoom.Client joined=new LanRoom.Client(dest,c,username,token,this);
            ui.post(()->{client=joined;room=c;hostIP=dest;connected=true;
                if(state==null||state.phase.equals("LOBBY"))page=Page.LOBBY;else page=Page.BATTLE;
                info="Connected to "+dest;view.invalidate();});
        }catch(Exception ex){toast("Join failed: "+ex.getMessage());}},"orefront-connect").start();}
    private void searchRooms(){info="Discovering rooms on Wi-Fi...";view.invalidate();new Thread(()->{
        List<LanRoom.RoomInfo> rooms=LanRoom.search(2400);
        ui.post(()->{if(rooms.isEmpty())new AlertDialog.Builder(this).setTitle("No rooms discovered")
            .setMessage("Broadcast discovery might be blocked by this hotspot. Use the room code + Host IP instead.")
            .setPositiveButton("Join manually",(d,w)->chooseName(this::manualJoin)).setNegativeButton("Close",null).show();
            else{String[] labels=new String[rooms.size()];for(int i=0;i<rooms.size();i++)labels[i]=rooms.get(i).toString();
                new AlertDialog.Builder(this).setTitle("LAN rooms nearby").setItems(labels,(d,n)->chooseName(()->join(rooms.get(n).code,rooms.get(n).host)))
                    .setNegativeButton("Cancel",null).show();}info="";view.invalidate();});
    },"orefront-discovery").start();}
    private boolean command(String order){
        if(offline){if(state==null)return false;String[] c=order.split("\\|");boolean ok=false;
            try{switch(c[0]){
                case "BUILD":ok=state.build(0,c[1],Integer.parseInt(c[2]),Integer.parseInt(c[3]));break;
                case "UPGRADE":ok=state.upgrade(0,Integer.parseInt(c[1]));break;
                case "TRAIN":ok=state.recruit(0,c[1]);break;
                case "MOVE":ok=state.move(0,Integer.parseInt(c[1]),Integer.parseInt(c[2]),Integer.parseInt(c[3]));break;
                default:break;
            }}catch(Exception ignored){}
            if(!ok)toast("Order rejected: check resources, placement or prerequisites.");view.invalidate();return ok;
        }
        if(server!=null)return server.command(order);
        return client!=null&&client.command(order);
    }
    private void configure(String field,String val){command("CFG|"+field+"|"+val);}
    private boolean amHost(){return server!=null;}
    private void takeoverDialog(){if(server==null||state==null)return;
        ArrayList<GameCore.Player> absent=new ArrayList<>();for(GameCore.Player p:state.players.values())if(!p.bot&&!p.connected)absent.add(p);
        if(absent.isEmpty()){toast("No disconnected players");return;}
        String[] names=new String[absent.size()];for(int i=0;i<names.length;i++)names[i]=absent.get(i).name+(absent.get(i).ai?" [AI enabled]":" [AI disabled]");
        new AlertDialog.Builder(this).setTitle("Host: AI control for disconnected faction")
            .setItems(names,(d,n)->{GameCore.Player p=absent.get(n);command("AI_TAKEOVER|"+p.id+"|"+(p.ai?"0":"1"));})
            .setNegativeButton("Cancel",null).show();
    }
    private void actionChoice(String choice){if(state==null||!state.phase.equals("PLAY"))return;
        if(Arrays.asList(GameCore.BUILDINGS).contains(choice)){
            placement=placement.equals(choice)?"":choice;info="Tap a free tile close to your HQ to build "+choice;
        }else if(Arrays.asList(GameCore.TROOPS).contains(choice)){command("TRAIN|"+choice);placement="";}
        else if(choice.equals("UPGRADE")){if(selectedBuilding<0)toast("Select one of your buildings first");else command("UPGRADE|"+selectedBuilding);}
        else if(choice.equals("AI CONTROL")){if(amHost())takeoverDialog();else toast("Only the host can assign AI takeover");}
        view.invalidate();
    }
    private void mapTapped(float sx,float sy){if(state==null||!state.phase.equals("PLAY"))return;
        // The hit test is the exact inverse of the diamond-centre isometric projection.
        int[] tile=view.camera.tileAt(sx,sy);
        int col=tile[0],row=tile[1];
        if(!GameCore.in(col,row))return;
        if(!placement.isEmpty()){command("BUILD|"+placement+"|"+col+"|"+row);placement="";view.invalidate();return;}
        for(GameCore.Unit u:state.units)if(u.owner==myId&&u.x==col&&u.y==row){
            selectedUnit=u.id;selectedBuilding=-1;info=u.type+" selected • tap destination";view.invalidate();return;}
        for(GameCore.Building bld:state.buildings)if(bld.owner==myId&&bld.x==col&&bld.y==row){
            selectedBuilding=bld.id;selectedUnit=-1;info=bld.type+" Lv"+bld.level+" • select UPGRADE";view.invalidate();return;}
        if(selectedUnit>=0){command("MOVE|"+selectedUnit+"|"+col+"|"+row);info="Movement order sent";}
        else info="Select a unit, or tap BUILD then a nearby tile";
        view.invalidate();
    }

    private final class GameView extends View {
        private final Paint p=new Paint(3);
        private final ArrayList<Hit> hits=new ArrayList<>();
        private final int BG=0xFF071423,PA=0xFF10293A,PL=0xFF163749,LINE=0xFF2E6977,
            WHITE=0xFFE6F9FA,MUTED=0xFF95B8C5,CYAN=0xFF50E5E4,GOLD=0xFFFFBE4D,RED=0xFFFF666B,GREEN=0xFF63E8A9;
        private final int[] faction={0xFF3EB5FF,0xFFFF666B,0xFF66DD8E,0xFFFFCF4C,0xFFBC84FF,0xFF5FDFDB};
        private static final float ISOX=IsoCamera.ORIGIN_X, ISOY=IsoCamera.ORIGIN_Y;
        private static final float TW=IsoCamera.TILE_W,TH=IsoCamera.TILE_H;
        private final IsoCamera camera=new IsoCamera();
        private float downX,downY,lastX,lastY,pinchDistance,pinchZoom;
        private boolean mapGesture,gestureMoved,pinchActive;
        private final HashMap<Integer,float[]> visualPositions=new HashMap<>();
        GameView(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        private int shade(int argb,float factor){
            int r=Math.min(255,Math.max(0,(int)(((argb>>16)&255)*factor)));
            int g=Math.min(255,Math.max(0,(int)(((argb>>8)&255)*factor)));
            int b=Math.min(255,Math.max(0,(int)((argb&255)*factor)));
            return (argb&0xFF000000)|(r<<16)|(g<<8)|b;
        }
        private void oval(Canvas c,float x,float y,float w,float h,int color){
            p.setStyle(Paint.Style.FILL);p.setColor(color);
            c.drawOval(new RectF(x-w/2,y-h/2,x+w/2,y+h/2),p);
        }
        private void polygon(Canvas c,int color,float... points){
            Path path=new Path();path.moveTo(points[0],points[1]);
            for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);
            path.close();p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawPath(path,p);
        }
        private void fauxGlow(Canvas c,float x,float y,float r,int color){
            circ(c,x,y,r*1.8f,(color&0x00FFFFFF)|0x18000000);
            circ(c,x,y,r*1.25f,(color&0x00FFFFFF)|0x33000000);
            circ(c,x,y,r,color);
        }
        private void fill(Canvas c,int color){c.drawColor(color);}
        private void rect(Canvas c,float x,float y,float w,float h,int col,float rad){p.setColor(col);p.setStyle(Paint.Style.FILL);c.drawRoundRect(new RectF(x,y,x+w,y+h),rad,rad,p);}
        private void stroke(Canvas c,float x,float y,float w,float h,int col,float rad,float thick){p.setColor(col);p.setStrokeWidth(thick);p.setStyle(Paint.Style.STROKE);c.drawRoundRect(new RectF(x,y,x+w,y+h),rad,rad,p);p.setStyle(Paint.Style.FILL);}
        private void ln(Canvas c,float x,float y,float xx,float yy,int col,float thick){p.setStyle(Paint.Style.STROKE);p.setColor(col);p.setStrokeWidth(thick);c.drawLine(x,y,xx,yy,p);p.setStyle(Paint.Style.FILL);}
        private void txt(Canvas c,String s,float x,float base,float size,int col,boolean bold){p.setStyle(Paint.Style.FILL);
            p.setColor(col);p.setTypeface(Typeface.create(bold?"sans-serif-condensed":"sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextSize(size);c.drawText(s,x,base,p);}
        private void center(Canvas c,String s,float x,float base,float size,int col,boolean bold){p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif-condensed",bold?Typeface.BOLD:Typeface.NORMAL));txt(c,s,x-p.measureText(s)/2f,base,size,col,bold);}
        private void circ(Canvas c,float x,float y,float r,int col){p.setStyle(Paint.Style.FILL);p.setColor(col);c.drawCircle(x,y,r,p);}
        private void diamond(Canvas c,float x,float y,float w,float h,int col){Path path=new Path();path.moveTo(x,y-h/2);path.lineTo(x+w/2,y);path.lineTo(x,y+h/2);path.lineTo(x-w/2,y);path.close();p.setColor(col);p.setStyle(Paint.Style.FILL);c.drawPath(path,p);}
        private void prism(Canvas c,float x,float y,float w,float h,float depth,int top,int left,int right){
            Path l=new Path();l.moveTo(x-w/2,y);l.lineTo(x,y+h/2);l.lineTo(x,y+h/2+depth);l.lineTo(x-w/2,y+depth);l.close();p.setColor(left);c.drawPath(l,p);
            Path r=new Path();r.moveTo(x+w/2,y);r.lineTo(x,y+h/2);r.lineTo(x,y+h/2+depth);r.lineTo(x+w/2,y+depth);r.close();p.setColor(right);c.drawPath(r,p);diamond(c,x,y,w,h,top);
        }
        private void btn(Canvas c,String s,float x,float y,float w,float h,boolean special,Runnable onClick){
            rect(c,x,y,w,h,special?0xFF176364:PA,11);stroke(c,x,y,w,h,special?CYAN:LINE,11,special?2.1f:1.4f);
            center(c,s,x+w/2,y+h/2+7,22,special?WHITE:WHITE,true);
            if(onClick!=null)hits.add(new Hit(new RectF(x,y,x+w,y+h),onClick));
        }
        private void small(Canvas c,String s,float x,float y,float w,float h,Runnable a){btn(c,s,x,y,w,h,false,a);}
        private void panel(Canvas c,float x,float y,float w,float h){rect(c,x,y,w,h,PA,15);stroke(c,x,y,w,h,LINE,15,1.1f);}
        private void grid(Canvas c){for(int i=0;i<=15;i++){ln(c,i*52,0,i*52,1280,0x152A5B73,1);ln(c,0,i*94,720,i*94,0x13285F7B,1);}}
        private void header(Canvas c,String kicker){rect(c,0,0,720,96,0xFF081C2C,0);ln(c,0,95,720,95,LINE,2);
            txt(c,"◆",24,62,40,CYAN,true);txt(c,"OREFRONT",78,54,35,WHITE,true);txt(c,"AI COMMAND WARS",81,81,17,GOLD,true);
            txt(c,kicker,472,57,15,CYAN,true);}
        private void tinyCircuit(Canvas c,float ox,float oy){for(int i=0;i<8;i++){
            float y=oy+i*47;ln(c,ox,y,ox+105+i%3*19,y,0x2C3C7992,1.6f);circ(c,ox+105+i%3*19,y,3,0xFF2F7587);}}
        private void creatureBadge(Canvas c,int who,float x,float y,int r){circ(c,x,y,r+4,0xFF162E40);circ(c,x,y,r,faction[who%6]);center(c,"P"+(who+1),x,y+6,r>16?18:13,0xFF03121E,true);}
        private static final class Hit {final RectF zone;final Runnable action;Hit(RectF z,Runnable a){zone=z;action=a;}}
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/720f,getHeight()/1280f);fill(c,BG);grid(c);hits.clear();
            if(page==Page.HOME)home(c);else if(page==Page.LOBBY)lobby(c);else battle(c);
            if(page!=Page.BATTLE&&info!=null&&!info.isEmpty()){rect(c,14,1235,692,34,0xEE17374A,9);txt(c,info.substring(0,Math.min(info.length(),72)),26,1258,16,GOLD,false);}
            c.restore();
            // Only redraw animation while the battlefield is visible.
            if(page==Page.BATTLE)postInvalidateDelayed(50);
        }
        private void home(Canvas c){header(c,"TACTICAL RTS");
            // Hero artwork made of lightweight vector shapes, not a copied game sprite.
            rect(c,20,115,680,360,0xFF0B2A3C,16);stroke(c,20,115,680,360,0xFF246C7F,16,2);
            tinyCircuit(c,37,160);tinyCircuit(c,530,177);
            circ(c,360,253,112,0xFF123E55);circ(c,360,253,78,0xFF17536A);
            diamond(c,360,282,200,112,0xFF2B6982);prism(c,360,258,122,67,34,0xFF244C64,0xFF103246,0xFF152A41);
            rect(c,340,185,40,79,0xFFABDBE8,6);rect(c,323,178,74,30,0xFF1B455B,8);
            circ(c,360,187,16,0xFF3DD5E7);ln(c,296,327,425,327,0xFF37BFE0,4);
            center(c,"COMMAND THE FRONTIER",360,386,31,WHITE,true);
            center(c,"6 FACTIONS  •  LAND / AIR  •  SMART AI",360,419,18,CYAN,true);
            center(c,"Build bases. Control sectors. Win the war.",360,447,18,MUTED,false);
            panel(c,20,493,680,256);
            txt(c,"CHOOSE YOUR BATTLE",41,530,23,WHITE,true);
            btn(c,"◈   PLAY AGAINST AI",44,547,632,78,true,()->startAI());
            rect(c,44,645,632,79,0xFF503920,12);stroke(c,44,645,632,79,GOLD,12,2);
            center(c,"⌘   LOCAL LAN MULTIPLAYER",360,694,23,WHITE,true);
            hits.add(new Hit(new RectF(44,645,676,724),()->chooseName(()->host())));
            panel(c,20,766,680,208);txt(c,"COMMANDER PROFILE",42,806,23,WHITE,true);
            creatureBadge(c,0,106,882,38);txt(c,username,169,858,30,WHITE,true);txt(c,"RANK  •  FIELD COMMANDER",169,888,18,GOLD,true);
            txt(c,"Mode: offline AI or 2–6 local players",42,932,17,MUTED,false);
            panel(c,20,993,680,221);
            txt(c,"MULTIPLAYER OPERATIONS",40,1033,23,CYAN,true);
            btn(c,"SEARCH LAN ROOMS",42,1047,306,63,false,()->searchRooms());
            btn(c,"JOIN BY CODE + IP",366,1047,310,63,false,()->chooseName(()->manualJoin()));
            txt(c,"No MacBook host. No internet for hotspot matches.",42,1146,17,MUTED,false);
            txt(c,"This version uses original lightweight vector graphics.",42,1184,16,MUTED,false);
        }
        private void lobby(Canvas c){GameCore w=state;if(w==null){header(c,"CONNECTING");center(c,"Opening LAN lobby...",360,560,30,WHITE,true);return;}
            header(c,amHost()?"HOST ONLINE":"LAN CLIENT");
            panel(c,18,113,684,176);txt(c,"LOCAL LAN ROOM • MAX SIX HUMANS",40,151,22,CYAN,true);
            txt(c,"ROOM CODE",41,199,17,MUTED,true);center(c,w.roomCode,360,214,45,GOLD,true);
            txt(c,"HOST IP: "+(hostIP.isEmpty()?"Connecting":hostIP),41,258,18,MUTED,false);
            btn(c,"LEAVE",565,235,114,38,false,()->leaveRoom());
            panel(c,18,303,684,444);txt(c,"COMMANDERS  "+w.connectedHumans()+" / "+w.maxPlayers,39,342,25,WHITE,true);
            txt(c,"Tap a player for host team / faction selection",39,367,16,MUTED,false);
            for(int i=0;i<6;i++){final int idx=i;float yy=380+i*56;
                rect(c,34,yy,652,51,selectedPlayer==i?0xFF1E4C56:0xFF112D3D,8);
                GameCore.Player pl=w.player(i);
                if(pl!=null){creatureBadge(c,i,63,yy+26,18);
                    txt(c,pl.name+(i==0?"  ★ HOST":""),91,yy+23,18,WHITE,true);
                    txt(c,GameCore.FACTIONS[pl.faction]+"  •  TEAM "+(pl.team+1),91,yy+42,14,MUTED,false);
                    String status=pl.bot?"BOT":!pl.connected?"OFFLINE":pl.ready?"READY":"WAIT";
                    txt(c,status,575,yy+30,17,pl.ready?GREEN:GOLD,true);
                }else txt(c,"SLOT "+(i+1)+"  •  AVAILABLE",93,yy+33,17,MUTED,false);
                hits.add(new Hit(new RectF(35,yy,685,yy+51),()->{selectedPlayer=idx;invalidate();}));
            }
            panel(c,18,759,684,340);txt(c,"HOST MATCH SETTINGS",38,797,22,CYAN,true);
            String[] labels={"PLAYERS","TYPE","BATTLEFIELD","DURATION","RESOURCES","AI LEVEL","AI SLOTS"};
            String[] vals={w.maxPlayers+"",w.mode,GameCore.MAPS[w.map],w.matchMinutes+" MIN",
                new String[]{"STANDARD","BOOSTED","HIGH"}[w.startResources],new String[]{"EASY","MEDIUM","HARD"}[w.aiDifficulty],w.botCount+""};
            for(int i=0;i<labels.length;i++){final int ii=i;float yy=808+i*39;
                txt(c,labels[i],41,yy+26,17,MUTED,true);rect(c,291,yy,383,35,0xFF1D4455,7);
                center(c,vals[i]+(amHost()?"   ›":""),482,yy+25,17,WHITE,true);
                if(amHost())hits.add(new Hit(new RectF(291,yy,674,yy+35),()->cycleSetting(ii)));
            }
            if(amHost()){
                small(c,"TEAM +",32,1110,205,48,()->{
                    GameCore.Player p=w.player(selectedPlayer);if(p!=null)command("TEAM|"+p.id+"|"+((p.team+1)%6));
                    else toast("Select an occupied player; team editing needs CUSTOM mode");});
                small(c,"FACTION +",257,1110,215,48,()->{
                    GameCore.Player p=w.player(selectedPlayer);if(p!=null)command("FACTION|"+p.id+"|"+((p.faction+1)%6));});
            }
            GameCore.Player me=w.player(myId);
            btn(c,me!=null&&me.ready?"NOT READY":"I'M READY",34,1170,308,57,me!=null&&me.ready,()->command("READY|"+(w.player(myId)!=null&&w.player(myId).ready?"0":"1")));
            if(amHost())btn(c,"START MATCH",360,1170,326,57,true,()->command("START"));
            else txt(c,"Wait for the host to start",371,1206,19,GOLD,true);
            txt(c,"Phone 1 hosts. Phones 2–6 join over Wi-Fi / hotspot.",39,1249,17,MUTED,false);
        }
        private void cycleSetting(int i){if(state==null||!amHost())return;GameCore w=state;
            switch(i){case 0:configure("MAX",""+(w.maxPlayers>=6?2:w.maxPlayers+1));break;
                case 1:configure("MODE",GameCore.MODES[(Arrays.asList(GameCore.MODES).indexOf(w.mode)+1)%GameCore.MODES.length]);break;
                case 2:configure("MAP",""+((w.map+1)%3));break;
                case 3:configure("TIME",""+(w.matchMinutes==5?10:w.matchMinutes==10?15:w.matchMinutes==15?20:5));break;
                case 4:configure("RESOURCES",""+((w.startResources+1)%3));break;
                case 5:configure("AI",""+((w.aiDifficulty+1)%3));break;
                case 6:configure("BOTS",""+((w.botCount+1)%6));break;
                default:break;}}
        private void battle(Canvas c){GameCore w=state;if(w==null){home(c);return;}
            GameCore.Player me=w.player(myId);header(c,offline?"AI OFFLINE":"LAN "+w.roomCode);
            // Resource HUD: values are always local to this player, but the host syncs all players.
            rect(c,12,108,696,102,PA,14);stroke(c,12,108,696,102,LINE,14,1);
            if(me!=null){String[] names={"GOLD","OIL","IRON","ENERGY"};int[] resources={me.gold,me.oil,me.iron,me.energy};
                int[] col={GOLD,0xFF79D8FC,0xFFD0D6EB,CYAN};
                for(int i=0;i<4;i++){float x=23+i*173;txt(c,names[i],x,136,15,MUTED,true);
                    txt(c,""+resources[i],x,174,27,col[i],true);if(i<3)ln(c,x+161,122,x+161,191,LINE,1);}
                txt(c,"P"+(myId+1)+" • "+GameCore.FACTIONS[me.faction]+" | BASE "+me.baseHP+" HP",25,198,14,MUTED,true);}
            // Tactical battlefield frame and minimap.
            rect(c,12,217,696,546,0xFF0E3145,12);stroke(c,12,217,696,546,0xFF357584,12,1.8f);
            drawIsometricWorld(c,w);
            drawCameraControls(c);
            rect(c,24,727,430,28,0xE9102537,6);
            txt(c,"DRAG TO PAN  •  PINCH TO ZOOM  •  TAP TO COMMAND",33,746,14,CYAN,true);
            drawMiniMap(c,w,557,650,129,106);
            int sec=w.matchMinutes*60-w.elapsedTicks/2;sec=Math.max(0,sec);
            rect(c,22,224,238,39,0xE70C2436,8);txt(c,"◷  "+(sec/60)+":"+String.format(Locale.US,"%02d",sec%60),34,251,21,GOLD,true);
            txt(c,"MAP: "+GameCore.MAPS[w.map],275,252,18,WHITE,true);
            // Under-map controls, refined from the concept mockup.
            panel(c,12,776,696,440);
            rect(c,25,789,674,86,0xFF153748,10);txt(c,"SELECTED UNIT / STRUCTURE",43,816,16,CYAN,true);
            String sel="COMMAND CENTER";String detail="Select one of your units or buildings on the map";
            if(selectedUnit>=0)for(GameCore.Unit u:w.units)if(u.id==selectedUnit&&u.owner==myId){sel=u.type+"  •  HP "+u.hp;detail="Target: "+u.tx+","+u.ty+"  |  Tap another tile to move";}
            if(selectedBuilding>=0)for(GameCore.Building b:w.buildings)if(b.id==selectedBuilding&&b.owner==myId){sel=b.type+"   LV "+b.level;detail="HP "+b.hp+"  •  Tap UPGRADE to improve";}
            txt(c,sel,41,843,25,WHITE,true);txt(c,detail,42,863,15,MUTED,false);
            String[] tabs={"BUILD","UNITS","UPGRADE"};for(int i=0;i<3;i++){final int index=i;
                btn(c,tabs[i],25+i*228,891,214,51,tab==i,()->{tab=index;placement="";invalidate();});}
            if(tab==0){String[] items={"MINE","REFINERY","GENERATOR","BARRACKS","FACTORY","AIRFIELD","TURRET"};
                for(int i=0;i<items.length;i++){final String item=items[i];int col=i%3,row=i/3;
                    btn(c,item,25+col*228,951+row*55,214,49,item.equals(placement),()->actionChoice(item));}
            }else if(tab==1){String[] troops={"SOLDIER","TANK","AIRCRAFT","HERO"};
                for(int i=0;i<troops.length;i++){final String item=troops[i];btn(c,item,25+(i%2)*344,959+(i/2)*63,330,54,false,()->actionChoice(item));}
                txt(c,"Barracks → Soldiers | Factory → Tanks | Airfield → Aircraft",29,1111,15,MUTED,false);
            }else{btn(c,"UPGRADE SELECTED BUILDING",31,970,654,66,true,()->actionChoice("UPGRADE"));
                txt(c,"Upgrades improve structure HP and resource output.",34,1072,17,MUTED,false);
                if(amHost())small(c,"AI TAKEOVER FOR DISCONNECTED PLAYER",35,1091,649,49,()->actionChoice("AI CONTROL"));}
            String hint=!placement.isEmpty()?"PLACE "+placement+" • TAP A FREE TILE BY YOUR BASE":
                "AI INTEL • "+((offline||amHost())?w.intelligence(myId):intel);
            rect(c,18,1221,684,47,0xFF12384B,9);
            txt(c,hint.substring(0,Math.min(hint.length(),65)),27,1250,16,GOLD,true);
            if(!w.phase.equals("PLAY")){
                rect(c,51,427,619,233,0xF10A2335,16);stroke(c,51,427,619,233,GOLD,16,2);
                center(c,"MATCH FINISHED",360,486,37,GOLD,true);
                center(c,w.result,360,539,23,WHITE,true);
                btn(c,"RETURN TO MENU",101,564,518,68,true,()->leaveRoom());
            }
            btn(c,"EXIT",575,18,125,57,false,()->new AlertDialog.Builder(MainActivity.this)
                .setTitle("Leave match?").setMessage("Disconnecting keeps your faction at the host until the game ends.")
                .setNegativeButton("Cancel",null).setPositiveButton("Leave",(d,v)->leaveRoom()).show());
        }
        private void drawMiniMap(Canvas c,GameCore w,float x,float y,float width,float height){
            rect(c,x,y,width,height,0xEC081D2C,6);stroke(c,x,y,width,height,LINE,6,1);
            for(GameCore.Player pl:w.players.values())if(pl.baseHP>0){float px=x+(pl.baseX+0.5f)/GameCore.W*width;
                float py=y+(pl.baseY+0.5f)/GameCore.H*height;circ(c,px,py,5,faction[pl.id]);}
            for(GameCore.Sector s:w.sectors){float px=x+(s.x+.5f)/GameCore.W*width;
                float py=y+(s.y+.5f)/GameCore.H*height;circ(c,px,py,2.4f,s.owner<0?GOLD:faction[s.owner]);}
            txt(c,"N",x+width-16,y+16,12,WHITE,true);
        }
        private float px(int x,int y){return IsoCamera.worldX(x,y);}
        private float py(int x,int y){return IsoCamera.worldY(x,y);}
        private void drawCameraControls(Canvas c){
            rect(c,621,272,72,157,0xD9072031,11);stroke(c,621,272,72,157,0xFF6BC8D0,11,1);
            btn(c,"+",629,279,55,42,false,()->camera.zoomAt(360,480,camera.zoom()*1.24f));
            btn(c,"−",629,325,55,42,false,()->camera.zoomAt(360,480,camera.zoom()/1.24f));
            btn(c,"FIT",629,372,55,45,false,()->camera.reset());
            rect(c,532,224,159,34,0xD9072031,7);
            center(c,"ZOOM "+Math.round(camera.zoom()*100)+"%",612,247,18,CYAN,true);
        }
        private void drawIsometricWorld(Canvas c,GameCore w){
            // Match rules and network coordinates are unchanged. Only the viewport changes.
            c.save();c.clipRect(15,219,705,761);
            c.translate(IsoCamera.VIEW_CENTER_X+camera.panX(),IsoCamera.VIEW_CENTER_Y+camera.panY());
            c.scale(camera.zoom(),camera.zoom());
            c.translate(-IsoCamera.VIEW_CENTER_X,-IsoCamera.VIEW_CENTER_Y);
            final long t=System.currentTimeMillis();
            // Painter's-order rendering for overlapping roofs and upright unit shapes.
            for(int sum=0;sum<GameCore.W+GameCore.H-1;sum++)for(int x=0;x<GameCore.W;x++){
                int y=sum-x;if(y<0||y>=GameCore.H)continue;
                float xx=px(x,y),yy=py(x,y);boolean rock=w.rock[x][y];
                int seed=(x*17+y*31+w.map*41);
                int grass=w.map==1?0xFF836A48:w.map==2?0xFF397C73:0xFF527A62;
                int tile=(seed%7==0)?shade(grass,1.13f):(seed%5==0)?shade(grass,.86f):grass;
                boolean road=!rock&&(x==8||x==9||y==5||((x+y)%13==0));
                if(road)tile=w.map==1?0xFFA18B60:0xFF69877C;
                if(rock){
                    oval(c,xx,yy+14,37,13,0x6604131F);
                    prism(c,xx,yy-4,TW,TH,9,0xFF748A93,0xFF314F5C,0xFF2A4351);
                    diamond(c,xx,yy-12,24,16,0xFFA0B2AD);
                    ln(c,xx-8,yy-11,xx+6,yy-16,0xFFC4CAC2,1f);
                }else{
                    prism(c,xx,yy,TW,TH,4,tile,shade(tile,.55f),shade(tile,.69f));
                    if(road){ln(c,xx-14,yy-1,xx+6,yy+6,0x4AC5D3BC,1.1f);}
                    if(seed%23==3){
                        oval(c,xx+1,yy+2,12,5,0x66031219);
                        rect(c,xx-2,yy-12,4,13,0xFF72573C,1);
                        polygon(c,0xFF1C6651,xx-10,yy-15,xx,yy-30,xx+11,yy-15);
                        polygon(c,0xFF349879,xx-8,yy-21,xx,yy-33,xx+8,yy-21);
                    }else if(seed%29==8){
                        oval(c,xx,yy+2,17,6,0x66031219);
                        diamond(c,xx,yy-6,16,10,0xFF97A9A0);
                    }
                }
                for(GameCore.Sector sector:w.sectors)if(sector.x==x&&sector.y==y){
                    int flag=sector.owner<0?GOLD:faction[sector.owner];
                    diamond(c,xx,yy-2,35,23,(flag&0x00FFFFFF)|0xBB000000);
                    oval(c,xx,yy-6,15,7,0xBB091B28);
                    prism(c,xx,yy-11,16,10,8,flag,shade(flag,.52f),shade(flag,.72f));
                    ln(c,xx,yy-26,xx,yy-44,0xFFD3EDF3,1.8f);
                    polygon(c,flag,xx,yy-45,xx+14,yy-40,xx,yy-35);
                }
                for(GameCore.Player player:w.players.values())if(player.baseHP>0&&player.baseX==x&&player.baseY==y)
                    drawHQ(c,xx,yy,player,t);
                for(GameCore.Building b:w.buildings)if(b.x==x&&b.y==y)drawBuilding(c,xx,yy,b,t);
                for(GameCore.Unit u:w.units)if(u.x==x&&u.y==y){
                    float[] visual=visualPositions.get(u.id);
                    if(visual==null){visual=new float[]{u.x,u.y};visualPositions.put(u.id,visual);}
                    visual[0]+=(u.x-visual[0])*.2f;visual[1]+=(u.y-visual[1])*.2f;
                    drawUnit(c,IsoCamera.worldX(visual[0],visual[1]),IsoCamera.worldY(visual[0],visual[1]),u,t);
                }
            }
            c.restore();
        }
        private void drawHQ(Canvas c,float x,float y,GameCore.Player pl,long time){
            int f=faction[pl.id];float pulse=(float)(.5+.5*Math.sin(time/550.0+pl.id));
            oval(c,x,y+12,51,20,0x99081926);
            prism(c,x,y-3,43,30,12,shade(f,.76f),shade(f,.42f),shade(f,.60f));
            prism(c,x,y-20,36,22,22,0xFF7B9BAA,0xFF29465A,0xFF1F3A51);
            // Two elevated guard towers and central command core.
            prism(c,x-12,y-24,12,8,18,shade(f,.85f),0xFF244458,0xFF2E4D62);
            prism(c,x+12,y-24,12,8,18,shade(f,.85f),0xFF244458,0xFF2E4D62);
            rect(c,x-10,y-52,20,16,0xFF355C72,3);
            diamond(c,x,y-54,28,17,shade(f,1.13f));
            rect(c,x-4,y-51,8,10,CYAN,2);
            fauxGlow(c,x,y-49,3+1.3f*pulse,CYAN);
            ln(c,x+13,y-55,x+21,y-72,0xFFADC7D8,2f);
            polygon(c,f,x+21,y-72,x+34,y-67,x+21,y-62);
            rect(c,x-19,y+20,38,4,0xFF4D2633,2);
            rect(c,x-19,y+20,38*Math.max(0,pl.baseHP)/260f,4,GREEN,2);
            center(c,"P"+(pl.id+1),x,y+36,14,WHITE,true);
        }
        private void drawBuilding(Canvas c,float x,float y,GameCore.Building b,long time){
            int f=faction[b.owner];
            oval(c,x,y+10,41,15,0x77030E19);
            prism(c,x,y-4,35,22,9,shade(f,.72f),shade(f,.48f),shade(f,.59f));
            String type=b.type;
            switch(type){
                case "MINE":
                    prism(c,x,y-18,27,15,14,0xFF8EADBA,0xFF354D60,0xFF526477);
                    polygon(c,GOLD,x-9,y-40,x-2,y-55,x+7,y-43,x+3,y-31);
                    diamond(c,x+8,y-31,12,9,0xFFE4B64D);break;
                case "REFINERY":
                    prism(c,x,y-20,28,16,14,0xFF708A9B,0xFF2F5166,0xFF3A6374);
                    rect(c,x-11,y-52,5,28,0xFF9AAEBC,2);
                    rect(c,x+5,y-41,7,19,0xFF67AEC3,3);
                    fauxGlow(c,x+8,y-32,3,0xFF69D0F0);break;
                case "GENERATOR":
                    prism(c,x,y-19,28,17,13,0xFF9DB4B8,0xFF325567,0xFF304C5A);
                    rect(c,x-7,y-48,14,29,0xFF227587,3);
                    diamond(c,x,y-48,23,12,CYAN);
                    fauxGlow(c,x,y-49,3+(float)Math.sin(time/350.)*1.0f,CYAN);break;
                case "BARRACKS":
                    prism(c,x,y-20,31,19,12,0xFF668DA5,0xFF203F57,0xFF2D526A);
                    rect(c,x-12,y-32,24,10,0xFF7E9EAC,2);
                    diamond(c,x,y-40,30,17,f);
                    ln(c,x-11,y-30,x+11,y-30,0xFFBEE9E9,2);break;
                case "FACTORY":
                    prism(c,x,y-20,31,18,17,0xFF9EABB5,0xFF3C4D63,0xFF365568);
                    rect(c,x-10,y-48,5,17,0xFF748FA2,1);
                    rect(c,x+5,y-43,5,15,0xFF748FA2,1);
                    diamond(c,x,y-46,26,12,shade(f,.92f));break;
                case "AIRFIELD":
                    prism(c,x,y-14,34,22,6,0xFF688A98,0xFF294456,0xFF315468);
                    polygon(c,0xFFE0F4ED,x-12,y-27,x+4,y-34,x+13,y-25,x+2,y-23);
                    ln(c,x-15,y-12,x+14,y-12,CYAN,2);break;
                case "TURRET":
                    prism(c,x,y-19,28,19,13,0xFF657F95,0xFF2C475F,0xFF37516B);
                    oval(c,x,y-37,17,9,0xFF364A5D);
                    rect(c,x-6,y-45,13,11,shade(f,.92f),4);
                    ln(c,x+3,y-43,x+20,y-53,0xFF1A374C,6);
                    if(time%950<400)fauxGlow(c,x+20,y-53,2.2f,GOLD);break;
                default:break;
            }
            ln(c,x-17,y-12,x+17,y-12,f,2.4f);
            if(selectedBuilding==b.id){
                diamond(c,x,y+8,49,24,0x6650E8E4);
                center(c,"LV "+b.level,x,y+29,13,GOLD,true);
            }
        }
        private void drawUnit(Canvas c,float x,float y,GameCore.Unit u,long time){
            int f=faction[u.owner];float uy=y-7;
            float bob=u.type.equals("AIRCRAFT")?(float)Math.sin(time/230.+u.id)*2:0;
            oval(c,x,uy+11,u.type.equals("AIRCRAFT")?36:27,10,0x99061520);
            if(selectedUnit==u.id)diamond(c,x,uy+11,46,23,0xAAFFD85C);
            switch(u.type){
                case "SOLDIER":
                    polygon(c,0xFF162F42,x-6,uy-2,x-1,uy+8,x+5,uy+8,x+7,uy-3);
                    rect(c,x-6,uy-13,12,13,f,3);
                    circ(c,x,uy-17,5,0xFFD0DBDC);
                    rect(c,x+5,uy-14,10,3,0xFF16283A,1);break;
                case "TANK":
                    polygon(c,0xFF233649,x-17,uy-8,x-13,uy+7,x+12,uy+7,x+16,uy-8);
                    polygon(c,shade(f,.72f),x-14,uy-13,x+9,uy-13,x+15,uy-6,x-10,uy-6);
                    rect(c,x-7,uy-17,14,10,shade(f,1.18f),3);
                    ln(c,x+2,uy-15,x+22,uy-25,0xFF28384A,5);
                    rect(c,x-14,uy+4,29,4,0xFF142A37,2);break;
                case "AIRCRAFT":
                    oval(c,x+2,uy+14,31,10,0x66020A13);
                    polygon(c,0xFFDAF4F4,x,uy-48+bob,x+7,uy-25+bob,x+21,uy-21+bob,
                        x+16,uy-15+bob,x,uy-19+bob,x-16,uy-15+bob,x-21,uy-21+bob,x-7,uy-25+bob);
                    polygon(c,f,x,uy-42+bob,x+5,uy-26+bob,x,uy-23+bob,x-5,uy-26+bob);
                    ln(c,x-4,uy-14+bob,x-3,uy-6+bob,CYAN,2);
                    ln(c,x+4,uy-14+bob,x+3,uy-6+bob,CYAN,2);break;
                default:
                    oval(c,x,uy+4,24,9,GOLD);
                    polygon(c,shade(f,.65f),x-11,uy-15,x+11,uy-15,x+13,uy+6,x-13,uy+6);
                    rect(c,x-8,uy-25,16,16,f,3);
                    circ(c,x,uy-29,8,0xFFE3CFAB);
                    ln(c,x-16,uy-18,x+14,uy-21,0xFFFFDE99,4);break;
            }
            float ratio=Math.max(0f,Math.min(1f,u.hp/(u.type.equals("TANK")?100f:u.type.equals("HERO")?135f:u.type.equals("AIRCRAFT")?65f:52f)));
            rect(c,x-15,uy+19,30,4,0xFF4A2735,2);
            rect(c,x-15,uy+19,ratio*30,4,GREEN,2);
            if(u.cd>0 && !u.type.equals("AIRCRAFT"))fauxGlow(c,x+13,uy-21,2.1f,GOLD);
        }
        private boolean inBattleMap(float x,float y){return x>=15&&x<=705&&y>=219&&y<=761;}
        private float xAt(MotionEvent event,int i){return event.getX(i)*720f/getWidth();}
        private float yAt(MotionEvent event,int i){return event.getY(i)*1280f/getHeight();}
        private float fingerGap(MotionEvent e){float dx=xAt(e,0)-xAt(e,1),dy=yAt(e,0)-yAt(e,1);
            return (float)Math.hypot(dx,dy);}
        @Override public boolean onTouchEvent(MotionEvent ev){
            int action=ev.getActionMasked();float x=xAt(ev,0),y=yAt(ev,0);
            if(action==MotionEvent.ACTION_DOWN){
                downX=lastX=x;downY=lastY=y;
                mapGesture=page==Page.BATTLE&&inBattleMap(x,y);
                gestureMoved=false;pinchActive=false;return true;
            }
            if(action==MotionEvent.ACTION_POINTER_DOWN&&ev.getPointerCount()>=2&&mapGesture){
                pinchActive=true;gestureMoved=true;pinchDistance=Math.max(1,fingerGap(ev));
                pinchZoom=camera.zoom();return true;
            }
            if(action==MotionEvent.ACTION_MOVE&&mapGesture){
                if(ev.getPointerCount()>=2&&pinchActive){
                    float midX=(xAt(ev,0)+xAt(ev,1))/2f;
                    float midY=(yAt(ev,0)+yAt(ev,1))/2f;
                    camera.zoomAt(midX,midY,pinchZoom*fingerGap(ev)/pinchDistance);
                    invalidate();return true;
                }
                if(!pinchActive&&Math.hypot(x-downX,y-downY)>10)gestureMoved=true;
                if(gestureMoved&&!pinchActive){camera.move(x-lastX,y-lastY);invalidate();}
                lastX=x;lastY=y;return true;
            }
            if(action==MotionEvent.ACTION_POINTER_UP){gestureMoved=true;pinchActive=true;return true;}
            if(action==MotionEvent.ACTION_UP){
                if(!gestureMoved&&!pinchActive){
                    for(int i=hits.size()-1;i>=0;i--){Hit hit=hits.get(i);
                        if(hit.zone.contains(x,y)){hit.action.run();invalidate();return true;}}
                    if(page==Page.BATTLE&&inBattleMap(x,y))mapTapped(x,y);
                }
                pinchActive=false;mapGesture=false;invalidate();return true;
            }
            if(action==MotionEvent.ACTION_CANCEL){pinchActive=false;mapGesture=false;return true;}
            return true;
        }
    }
}
