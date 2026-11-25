package com.student.orefront;

import java.util.*;

/** Pure-Java authoritative strategy simulation. No Android dependencies. */
public final class GameCore {
    public static final int W=18,H=12, MAX_HUMANS=6;
    public static final String[] COLORS={"#39C8FF","#FF7168","#67E48D","#F6CF5A","#B58AFF","#63DED8"};
    public static final String[] FACTIONS={"Vanguard","Ironclad","Skyguard","Rangers","Nova","Sentinel"};
    public static final String[] MAPS={"Highlands","Dust Frontier","Island Siege"};
    public static final String[] MODES={"1v1","2v2","3v3","FFA","CUSTOM"};
    public static final String[] BUILDINGS={"MINE","REFINERY","GENERATOR","BARRACKS","FACTORY","AIRFIELD","TURRET"};
    public static final String[] TROOPS={"SOLDIER","TANK","AIRCRAFT","HERO"};
    public static final class Player {
        public int id,team,faction,baseX,baseY,baseHP=260, gold,oil,iron,energy,score;
        public String name="",token=""; public boolean ready,connected,ai,bot;
        public Player(int id,String name,String token) {this.id=id;this.name=clean(name);this.token=token;team=id;faction=id%6;}
    }
    public static final class Building {
        public int id,owner,x,y,level=1,hp=110; public String type;
        public Building(int id,int owner,String type,int x,int y){this.id=id;this.owner=owner;this.type=type;this.x=x;this.y=y;}
    }
    public static final class Unit {
        public int id,owner,x,y,tx,ty,hp,cd;public String type;
        public Unit(int id,int owner,String type,int x,int y){this.id=id;this.owner=owner;this.type=type;this.x=x;this.y=y;tx=x;ty=y;hp=type.equals("TANK")?100:type.equals("HERO")?135: type.equals("AIRCRAFT")?65:52;}
    }
    public static final class Sector {public int x,y,owner=-1;Sector(int x,int y){this.x=x;this.y=y;}}
    public final TreeMap<Integer,Player> players=new TreeMap<>();
    public final ArrayList<Building> buildings=new ArrayList<>();
    public final ArrayList<Unit> units=new ArrayList<>();
    public final ArrayList<Sector> sectors=new ArrayList<>();
    public int maxPlayers=6,map=0,matchMinutes=10,startResources=0,aiDifficulty=1,botCount=0;
    public String mode="FFA",roomCode="",phase="LOBBY",result="",lastEvent="Room waiting for players";
    public int elapsedTicks=0,nextId=1,revision=0,winningTeam=-1;
    public boolean[][] rock=new boolean[W][H];
    private final Random random=new Random(7821);
    public GameCore(){initMap();}
    static String clean(String s){
        String cleaned=s==null?"":s.replaceAll("[^a-zA-Z0-9 _-]","").trim();
        if(cleaned.isEmpty())cleaned="Player";
        return cleaned.substring(0,Math.min(18,cleaned.length()));
    }
    public void initMap(){
        rock=new boolean[W][H];
        for(int x=0;x<W;x++)for(int y=0;y<H;y++){
            rock[x][y]=x==0||y==0||x==W-1||y==H-1;
            if(x>=4&&x<=13&&y>=3&&y<=8 && (x*11+y*7+map*5)%17==0)rock[x][y]=true;
        }
        sectors.clear(); sectors.add(new Sector(5,3));sectors.add(new Sector(12,3));
        sectors.add(new Sector(5,8));sectors.add(new Sector(12,8));sectors.add(new Sector(9,5));
        for(Sector s:sectors)rock[s.x][s.y]=false;
        for(int[] p:SPAWNS)for(int a=p[0]-1;a<=p[0]+1;a++)for(int b=p[1]-1;b<=p[1]+1;b++)if(in(a,b))rock[a][b]=false;
        revision++;
    }
    private static final int[][] SPAWNS={{2,2},{15,9},{2,9},{15,2},{9,2},{9,9}};
    static boolean in(int x,int y){return x>=1&&x<W-1&&y>=1&&y<H-1;}
    public int humanCount(){int n=0;for(Player p:players.values())if(!p.bot)n++;return n;}
    public int connectedHumans(){int n=0;for(Player p:players.values())if(!p.bot&&p.connected)n++;return n;}
    public Player addHuman(String name,String token){
        if(!phase.equals("LOBBY")||humanCount()>=maxPlayers||humanCount()>=6)return null;
        for(int id=0;id<6;id++)if(!players.containsKey(id)){
            String unique=clean(name);boolean exists=true;int suffix=2;
            while(exists){exists=false;for(Player other:players.values())if(other.name.equalsIgnoreCase(unique)){exists=true;break;}
                if(exists){String base=clean(name);base=base.substring(0,Math.min(13,base.length()));unique=base+" "+(suffix++);}
            }
            Player p=new Player(id,unique,token);p.connected=true;players.put(id,p);updateModeTeams();revision++;return p;
        }
        return null;
    }
    public Player reconnect(String token){
        if(token==null||token.isEmpty())return null;
        for(Player p:players.values())if(!p.bot&&token.equals(p.token)){p.connected=true;p.ai=false;revision++;return p;}
        return null;
    }
    public void disconnect(int id,boolean remove){Player p=players.get(id);if(p==null)return;p.connected=false;p.ready=false;if(remove&&phase.equals("LOBBY")&&id!=0)players.remove(id);revision++;}
    public boolean removePlayer(int id){if(!phase.equals("LOBBY")||id==0)return false;Player p=players.remove(id);if(p!=null){revision++;return true;}return false;}
    public boolean setConfig(String field,String value){if(!phase.equals("LOBBY"))return false;int n;
        try{switch(field){
            case "MAX":n=Integer.parseInt(value);if(n<2||n>6||n<humanCount())return false;maxPlayers=n;break;
            case "MODE":if(!Arrays.asList(MODES).contains(value))return false;mode=value;updateModeTeams();break;
            case "MAP":n=Integer.parseInt(value);if(n<0||n>=MAPS.length)return false;map=n;initMap();break;
            case "TIME":n=Integer.parseInt(value);if(n!=5&&n!=10&&n!=15&&n!=20)return false;matchMinutes=n;break;
            case "RESOURCES":n=Integer.parseInt(value);if(n<0||n>2)return false;startResources=n;break;
            case "AI":n=Integer.parseInt(value);if(n<0||n>2)return false;aiDifficulty=n;break;
            case "BOTS":n=Integer.parseInt(value);if(n<0||n>5)return false;botCount=n;break;
            default:return false;
        }revision++;return true;}catch(Exception ignored){return false;}
    }
    public void updateModeTeams(){for(Player p:players.values()){
        if(mode.equals("1v1"))p.team=p.id==0?0:1;
        else if(mode.equals("2v2")||mode.equals("3v3"))p.team=p.id%2;
        else if(mode.equals("FFA"))p.team=p.id;
    }}
    public boolean setTeam(int id,int team){Player p=players.get(id);if(p==null||!mode.equals("CUSTOM")||team<0||team>5)return false;p.team=team;revision++;return true;}
    public boolean setFaction(int id,int faction){Player p=players.get(id);if(p==null||faction<0||faction>=FACTIONS.length)return false;
        for(Player other:players.values())if(other.id!=id&&other.faction==faction)other.faction=p.faction;
        p.faction=faction;revision++;return true;}
    public boolean setReady(int id,boolean ready){Player p=players.get(id);if(p==null||p.bot||!p.connected||!phase.equals("LOBBY"))return false;p.ready=ready;revision++;return true;}
    public String startCheck(){
        if(!phase.equals("LOBBY"))return "Already started";
        if(connectedHumans()<2)return "At least 2 connected human players required";
        if(connectedHumans()!=humanCount())return "Reconnect disconnected players first";
        if(connectedHumans()+botCount>6)return "Human + AI factions cannot exceed six";
        for(Player p:players.values())if(!p.bot&&!p.ready)return p.name+" is not ready";
        if(mode.equals("1v1")&&humanCount()!=2)return "1v1 needs exactly 2 humans";
        if(mode.equals("2v2")&&humanCount()!=4)return "2v2 needs exactly 4 humans";
        if(mode.equals("3v3")&&humanCount()!=6)return "3v3 needs exactly 6 humans";
        if(mode.equals("FFA")&&humanCount()<2)return "FFA needs at least 2 humans";
        HashSet<Integer> teams=new HashSet<>();for(Player p:players.values())teams.add(p.team);
        if(teams.size()<2)return "At least two opposing teams are required";
        return "";
    }
    public boolean start(){if(!startCheck().isEmpty())return false;
        int reserve=botCount;
        for(int i=0;i<reserve;i++)for(int id=0;id<6;id++)if(!players.containsKey(id)){
            Player bot=new Player(id,"AI "+(i+1),"bot:"+id);bot.bot=true;bot.ai=true;bot.connected=true;players.put(id,bot);break;
        }
        if(mode.equals("FFA"))updateModeTeams();
        buildings.clear();units.clear();elapsedTicks=0;nextId=1;result="";winningTeam=-1;
        initMap();for(Sector s:sectors)s.owner=-1;
        for(Player p:players.values()){
            int[] xy=SPAWNS[p.id];p.baseX=xy[0];p.baseY=xy[1];p.baseHP=260;p.score=0;
            p.gold=startResources==0?320:startResources==1?600:1200;
            p.oil=startResources==0?180:startResources==1?320:700;
            p.iron=startResources==0?240:startResources==1?440:800;
            p.energy=startResources==0?160:startResources==1?280:550;
        }
        phase="PLAY";lastEvent="The battle begins. Capture sectors and defend bases!";revision++;return true;
    }
    public boolean addOfflineAI(int count,int difficulty){
        players.clear();phase="LOBBY";mode="FFA";aiDifficulty=Math.max(0,Math.min(2,difficulty));botCount=0;
        Player human=addHuman("COMMANDER","offline-host");human.ready=true;
        for(int i=0;i<Math.min(5,count);i++){Player p=new Player(i+1,"AI "+(i+1),"bot:"+i);p.bot=true;p.ai=true;p.connected=true;p.ready=true;players.put(p.id,p);}
        initMap();buildings.clear();units.clear();elapsedTicks=0;nextId=1;result="";winningTeam=-1;
        for(Player p:players.values()){
            int[] xy=SPAWNS[p.id];p.baseX=xy[0];p.baseY=xy[1];p.baseHP=260;
            p.gold=320;p.oil=180;p.iron=240;p.energy=160;
        }
        phase="PLAY";revision++;return true;
    }
    public Player player(int id){return players.get(id);}
    private boolean ownTile(Player p,int x,int y){if(Math.abs(x-p.baseX)+Math.abs(y-p.baseY)<=4)return true;for(Building b:buildings)if(b.owner==p.id&&Math.abs(x-b.x)+Math.abs(y-b.y)<=2)return true;return false;}
    private boolean taken(int x,int y){for(Player p:players.values())if(p.baseHP>0&&p.baseX==x&&p.baseY==y)return true;for(Building b:buildings)if(b.x==x&&b.y==y)return true;return false;}
    private boolean spend(Player p,int g,int o,int i,int e){if(p.gold<g||p.oil<o||p.iron<i||p.energy<e)return false;p.gold-=g;p.oil-=o;p.iron-=i;p.energy-=e;return true;}
    public boolean build(int owner,String type,int x,int y){Player p=players.get(owner);
        if(!phase.equals("PLAY")||p==null||p.baseHP<=0||!Arrays.asList(BUILDINGS).contains(type)||!in(x,y)||rock[x][y]||taken(x,y)||!ownTile(p,x,y))return false;
        if(buildings.stream().filter(b->b.owner==owner).count()>=14)return false;
        int g=100,o=20,i=70,e=15;
        if(type.equals("REFINERY")){g=110;o=0;i=90;e=10;}
        if(type.equals("GENERATOR")){g=90;o=30;i=70;e=0;}
        if(type.equals("BARRACKS")){g=130;o=35;i=110;e=30;}
        if(type.equals("FACTORY")){g=170;o=100;i=180;e=55;}
        if(type.equals("AIRFIELD")){g=220;o=145;i=170;e=95;}
        if(type.equals("TURRET")){g=165;o=45;i=125;e=55;}
        if(!spend(p,g,o,i,e))return false;
        buildings.add(new Building(nextId++,owner,type,x,y));p.score+=10;revision++;return true;
    }
    public boolean upgrade(int owner,int structureId){Player p=players.get(owner);if(p==null||!phase.equals("PLAY"))return false;
        for(Building b:buildings)if(b.id==structureId&&b.owner==owner&&b.level<3){if(!spend(p,90*b.level,25*b.level,80*b.level,35*b.level))return false;b.level++;b.hp+=70;p.score+=25;revision++;return true;}
        return false;
    }
    private Building facility(int owner,String type){for(Building b:buildings)if(b.owner==owner&&b.type.equals(type))return b;return null;}
    public boolean recruit(int owner,String type){Player p=players.get(owner);if(p==null||p.baseHP<=0||!phase.equals("PLAY")||!Arrays.asList(TROOPS).contains(type))return false;
        if(units.stream().filter(u->u.owner==owner).count()>=14)return false;
        String required=type.equals("SOLDIER")?"BARRACKS":type.equals("TANK")?"FACTORY":type.equals("AIRCRAFT")?"AIRFIELD":"";
        if(!required.isEmpty()&&facility(owner,required)==null)return false;
        if(type.equals("HERO"))for(Unit u:units)if(u.owner==owner&&u.type.equals("HERO"))return false;
        int g=65,o=10,i=35,e=10;
        if(type.equals("TANK")){g=120;o=80;i=120;e=20;}
        if(type.equals("AIRCRAFT")){g=135;o=105;i=70;e=90;}
        if(type.equals("HERO")){g=180;o=50;i=90;e=100;}
        if(!spend(p,g,o,i,e))return false;
        Building f=required.isEmpty()?null:facility(owner,required);
        int x=f==null?p.baseX:f.x,y=f==null?p.baseY:f.y;
        Unit u=new Unit(nextId++,owner,type,x,y);units.add(u);p.score+=15;revision++;return true;
    }
    public boolean move(int owner,int unitId,int x,int y){if(!phase.equals("PLAY")||!in(x,y)||rock[x][y])return false;
        for(Unit u:units)if(u.owner==owner&&u.id==unitId){u.tx=x;u.ty=y;revision++;return true;}return false;
    }
        private boolean hostile(int a,int b){Player x=players.get(a),y=players.get(b);return x!=null&&y!=null&&x.team!=y.team;}
    private int dist(int x,int y,int xx,int yy){return Math.abs(x-xx)+Math.abs(y-yy);}
    /** Shortest 4-direction route; rock tiles cannot be traversed. */
    public int[] nextStep(int sx,int sy,int tx,int ty){
        if(!in(sx,sy)||!in(tx,ty)||rock[tx][ty])return new int[]{sx,sy};
        int[][] seen=new int[W][H];for(int[] a:seen)Arrays.fill(a,-1);
        int[] qx=new int[W*H],qy=new int[W*H];int head=0,tail=0;qx[tail]=sx;qy[tail++]=sy;seen[sx][sy]=sx+W*sy;
        int[][] delta={{1,0},{-1,0},{0,1},{0,-1}};
        while(head<tail){int x=qx[head],y=qy[head++];if(x==tx&&y==ty)break;
            for(int[] d:delta){int xx=x+d[0],yy=y+d[1];if(in(xx,yy)&&!rock[xx][yy]&&seen[xx][yy]==-1){seen[xx][yy]=x+W*y;qx[tail]=xx;qy[tail++]=yy;}}
        }
        if(seen[tx][ty]<0)return new int[]{sx,sy};
        int x=tx,y=ty;while(!(x==sx&&y==sy)){int prev=seen[x][y],px=prev%W,py=prev/W;if(px==sx&&py==sy)return new int[]{x,y};x=px;y=py;}
        return new int[]{sx,sy};
    }
    public void tick(){if(!phase.equals("PLAY"))return;elapsedTicks++;
        if(elapsedTicks%2==0){for(Player p:players.values())if(p.baseHP>0){
            int mines=0,refs=0,gens=0;
            for(Building b:buildings)if(b.owner==p.id){if(b.type.equals("MINE"))mines+=b.level;if(b.type.equals("REFINERY"))refs+=b.level;if(b.type.equals("GENERATOR"))gens+=b.level;}
            int zones=0;for(Sector s:sectors)if(s.owner==p.id)zones++;
            p.gold+=10+mines*12+zones*5;p.iron+=8+mines*10;p.oil+=6+refs*15;p.energy+=6+gens*15;
            p.gold=Math.min(p.gold,6000);p.iron=Math.min(p.iron,6000);p.oil=Math.min(p.oil,6000);p.energy=Math.min(p.energy,6000);
            revision++;
        }}
        for(Player p:players.values())if(p.ai&&p.baseHP>0)aiTick(p);
        // Automated defensive turret fire, owned and resolved only by the authority.
        if(elapsedTicks%2==0)for(Building tower:buildings)if(tower.type.equals("TURRET")&&tower.hp>0){
            Unit target=null;int nearest=999;
            for(Unit enemy:units)if(enemy.hp>0&&hostile(tower.owner,enemy.owner)){
                int d=dist(tower.x,tower.y,enemy.x,enemy.y);
                if(d<=3&&d<nearest){nearest=d;target=enemy;}
            }
            if(target!=null){target.hp-=9+6*(tower.level-1);revision++;
                lastEvent=players.get(tower.owner).name+" turret defended the perimeter";}
        }
        for(Unit u:new ArrayList<>(units)){
            if(u.cd>0)u.cd--;
            if(u.hp<=0)continue;
            Unit closest=null;int nearest=99;
            for(Unit e:units)if(e.hp>0&&hostile(u.owner,e.owner)){
                int d=dist(u.x,u.y,e.x,e.y);if(d<nearest){nearest=d;closest=e;}}
            Player base=null;int baseDist=99;
            for(Player e:players.values())if(e.baseHP>0&&hostile(u.owner,e.id)){
                int d=dist(u.x,u.y,e.baseX,e.baseY);if(d<baseDist){baseDist=d;base=e;}}
            int range=u.type.equals("AIRCRAFT")?3: u.type.equals("TANK")?2:1;
            int damage=u.type.equals("TANK")?16:u.type.equals("HERO")?20:u.type.equals("AIRCRAFT")?13:10;
            if(u.cd==0&&closest!=null&&nearest<=range){closest.hp-=damage;u.cd=2;revision++;}
            else if(u.cd==0&&base!=null&&baseDist<=range){base.baseHP=Math.max(0,base.baseHP-damage);u.cd=3;revision++;}
            else if(u.type.equals("AIRCRAFT")||elapsedTicks%2==0){
                int[] next=nextStep(u.x,u.y,u.tx,u.ty);
                if(next[0]!=u.x||next[1]!=u.y){u.x=next[0];u.y=next[1];revision++;}
            }
        }
        units.removeIf(u->u.hp<=0);
        for(Sector s:sectors){int capturer=-1;boolean contested=false;
            for(Unit u:units)if(dist(u.x,u.y,s.x,s.y)<=1){if(capturer<0)capturer=u.owner;else if(hostile(capturer,u.owner)){contested=true;break;}}
            if(!contested&&capturer>=0&&s.owner!=capturer){s.owner=capturer;players.get(capturer).score+=35;lastEvent=players.get(capturer).name+" captured a sector";revision++;}
        }
        if(elapsedTicks>=matchMinutes*120){finishByScore();return;}
        HashSet<Integer> alive=new HashSet<>();for(Player p:players.values())if(p.baseHP>0)alive.add(p.team);
        if(alive.size()==1&&players.size()>=2){winningTeam=alive.iterator().next();phase="FINISHED";result="Team "+(winningTeam+1)+" wins by conquest";lastEvent=result;revision++;}
        else if(alive.isEmpty()){phase="FINISHED";result="Draw: all bases destroyed";lastEvent=result;revision++;}
    }
    private void finishByScore(){HashMap<Integer,Integer> totals=new HashMap<>();for(Player p:players.values()){
        int score=p.score+p.baseHP+Math.min(300,(p.gold+p.oil+p.iron+p.energy)/40);
        for(Unit u:units)if(u.owner==p.id)score+=20;
        totals.put(p.team,totals.getOrDefault(p.team,0)+score);
    }
        int best=Integer.MIN_VALUE,team=-1;boolean draw=false;
        for(Map.Entry<Integer,Integer> e:totals.entrySet()){if(e.getValue()>best){best=e.getValue();team=e.getKey();draw=false;}else if(e.getValue()==best)draw=true;}
        winningTeam=draw?-1:team;phase="FINISHED";result=draw?"Draw on points":"Team "+(team+1)+" wins on points";lastEvent=result;revision++;
    }
    private void aiTick(Player p){
        int delay=aiDifficulty==0?12:aiDifficulty==1?7:4;
        if(elapsedTicks%delay!=p.id%delay)return;
        if(facility(p.id,"MINE")==null){buildNear(p,"MINE");return;}
        if(facility(p.id,"BARRACKS")==null){buildNear(p,"BARRACKS");return;}
        if(facility(p.id,"REFINERY")==null&&elapsedTicks>35){buildNear(p,"REFINERY");return;}
        if(facility(p.id,"FACTORY")==null&&elapsedTicks>50){buildNear(p,"FACTORY");return;}
        if(facility(p.id,"TURRET")==null&&elapsedTicks>80){buildNear(p,"TURRET");return;}
        if(units.stream().filter(u->u.owner==p.id).count()<Math.min(14,4+elapsedTicks/35)){
            if(facility(p.id,"FACTORY")!=null&&elapsedTicks%3==0)recruit(p.id,"TANK");
            else recruit(p.id,"SOLDIER");
        }
        Player foe=null;int range=9999;
        for(Player enemy:players.values())if(enemy.baseHP>0&&hostile(p.id,enemy.id)){int d=dist(p.baseX,p.baseY,enemy.baseX,enemy.baseY);if(d<range){range=d;foe=enemy;}}
        if(foe!=null)for(Unit u:units)if(u.owner==p.id){
            if(elapsedTicks<80){Sector s=sectors.get((p.id+u.id)%sectors.size());u.tx=s.x;u.ty=s.y;}
            else {u.tx=foe.baseX;u.ty=foe.baseY;}
        }
    }
    private void buildNear(Player p,String type){for(int k=0;k<24;k++){
        int x=p.baseX+(k%5)-2,y=p.baseY+(k/5)-2;
        if(build(p.id,type,x,y))return;
    }}
    public String intelligence(int id){Player p=players.get(id);if(p==null)return "";
        int threat=0;for(Unit u:units)if(hostile(id,u.owner)&&dist(u.x,u.y,p.baseX,p.baseY)<=5)threat++;
        if(threat>0)return "INTEL: "+threat+" hostile units near your base!";
        if(p.baseHP<100)return "INTEL: CRITICAL - reinforce your command base";
        if(p.energy<55)return "INTEL: Energy low - build a generator";
        if(facility(id,"BARRACKS")==null)return "INTEL: Build barracks to unlock infantry";
        if(facility(id,"FACTORY")==null&&p.gold>250)return "INTEL: Build a factory to unlock tanks";
        int held=(int)sectors.stream().filter(s->s.owner==id).count();
        return "INTEL: "+held+" sectors held - secure the central control point";
    }
    /** Text snapshot: no private reconnect tokens are serialized. */
    public String encode(){StringBuilder b=new StringBuilder();
        b.append("H;").append(roomCode).append(';').append(phase).append(';').append(mode).append(';').append(maxPlayers).append(';').append(map).append(';').append(matchMinutes).append(';').append(startResources).append(';').append(aiDifficulty).append(';').append(botCount).append(';').append(elapsedTicks).append(';').append(nextId).append(';').append(winningTeam).append(';').append(clean(lastEvent)).append(';').append(clean(result)).append('\n');
        for(Player p:players.values())b.append("P;").append(p.id).append(';').append(clean(p.name)).append(';').append(p.team).append(';').append(p.faction).append(';').append(p.ready?1:0).append(';').append(p.connected?1:0).append(';').append(p.ai?1:0).append(';').append(p.bot?1:0).append(';').append(p.gold).append(';').append(p.oil).append(';').append(p.iron).append(';').append(p.energy).append(';').append(p.baseX).append(';').append(p.baseY).append(';').append(p.baseHP).append(';').append(p.score).append('\n');
        for(Building t:buildings)b.append("B;").append(t.id).append(';').append(t.owner).append(';').append(t.type).append(';').append(t.x).append(';').append(t.y).append(';').append(t.level).append(';').append(t.hp).append('\n');
        for(Unit u:units)b.append("U;").append(u.id).append(';').append(u.owner).append(';').append(u.type).append(';').append(u.x).append(';').append(u.y).append(';').append(u.tx).append(';').append(u.ty).append(';').append(u.hp).append(';').append(u.cd).append('\n');
        for(Sector s:sectors)b.append("S;").append(s.x).append(';').append(s.y).append(';').append(s.owner).append('\n');
        return b.toString();
    }
    public static GameCore decode(String text){GameCore w=new GameCore();w.players.clear();w.buildings.clear();w.units.clear();w.sectors.clear();
        for(String line:text.split("\\n")){if(line.isEmpty())continue;String[] c=line.split(";",-1);
            try{switch(c[0]){
                case "H":w.roomCode=c[1];w.phase=c[2];w.mode=c[3];w.maxPlayers=Integer.parseInt(c[4]);w.map=Integer.parseInt(c[5]);w.matchMinutes=Integer.parseInt(c[6]);w.startResources=Integer.parseInt(c[7]);w.aiDifficulty=Integer.parseInt(c[8]);w.botCount=Integer.parseInt(c[9]);w.elapsedTicks=Integer.parseInt(c[10]);w.nextId=Integer.parseInt(c[11]);w.winningTeam=Integer.parseInt(c[12]);w.lastEvent=c[13];w.result=c[14];w.initMap();w.sectors.clear();break;
                case "P":Player p=new Player(Integer.parseInt(c[1]),c[2],"");p.team=Integer.parseInt(c[3]);p.faction=Integer.parseInt(c[4]);p.ready=c[5].equals("1");p.connected=c[6].equals("1");p.ai=c[7].equals("1");p.bot=c[8].equals("1");p.gold=Integer.parseInt(c[9]);p.oil=Integer.parseInt(c[10]);p.iron=Integer.parseInt(c[11]);p.energy=Integer.parseInt(c[12]);p.baseX=Integer.parseInt(c[13]);p.baseY=Integer.parseInt(c[14]);p.baseHP=Integer.parseInt(c[15]);p.score=Integer.parseInt(c[16]);w.players.put(p.id,p);break;
                case "B":Building t=new Building(Integer.parseInt(c[1]),Integer.parseInt(c[2]),c[3],Integer.parseInt(c[4]),Integer.parseInt(c[5]));t.level=Integer.parseInt(c[6]);t.hp=Integer.parseInt(c[7]);w.buildings.add(t);break;
                case "U":Unit u=new Unit(Integer.parseInt(c[1]),Integer.parseInt(c[2]),c[3],Integer.parseInt(c[4]),Integer.parseInt(c[5]));u.tx=Integer.parseInt(c[6]);u.ty=Integer.parseInt(c[7]);u.hp=Integer.parseInt(c[8]);u.cd=Integer.parseInt(c[9]);w.units.add(u);break;
                case "S":Sector s=new Sector(Integer.parseInt(c[1]),Integer.parseInt(c[2]));s.owner=Integer.parseInt(c[3]);w.sectors.add(s);break;
                default:break;
            }}catch(Exception e){throw new IllegalArgumentException("Malformed state: "+c[0],e);}
        }
        return w;
    }
}
