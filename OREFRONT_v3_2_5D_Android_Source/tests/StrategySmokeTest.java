import com.student.orefront.GameCore;
import java.util.*;

public final class StrategySmokeTest {
    static void check(boolean condition,String explanation){if(!condition)throw new AssertionError(explanation);}
    public static void main(String[] args){
        GameCore w=new GameCore();w.roomCode="849214";
        for(int i=0;i<6;i++){GameCore.Player p=w.addHuman("Player"+(i+1),"test-token-"+i);check(p!=null&&p.id==i,"6 human slots");check(w.setReady(i,true),"ready");}
        check(w.addHuman("Seventh","test-7")==null,"cap rejects player seven");
        check(w.setConfig("MODE","3v3"),"configuration");
        check(w.startCheck().isEmpty(),"six humans may start 3v3");check(w.start(),"start");
        for(GameCore.Player p:w.players.values())check(p.gold>0&&p.baseHP>0,"unique faction resources");
        check(w.build(0,"MINE",3,2),"build mine");
        check(w.build(0,"BARRACKS",2,3),"build barracks");
        check(w.recruit(0,"SOLDIER"),"buy soldier");
        int unit=w.units.get(0).id;
        check(w.move(0,unit,5,3),"move soldier");
        check(!w.move(1,unit,6,5),"cannot control another player's unit");
        check(!w.build(1,"MINE",3,4),"cannot build near another HQ");
        for(int i=0;i<50;i++)w.tick();
        GameCore copy=GameCore.decode(w.encode());
        check(copy.players.size()==6,"snapshot contains 6 players");
        check(copy.sectors.size()==5,"state doesn't double-count sectors");
        check(copy.buildings.size()==2&&copy.units.size()==1,"state contains structures and units");
        check(copy.player(0).gold==w.player(0).gold,"resources synchronized");
        check(copy.rock[0][0],"terrain restored");
        int hp=w.player(2).baseHP;w.disconnect(2,false);
        check(w.player(2).baseHP==hp,"disconnection preserves base");
        check(w.phase.equals("PLAY"),"disconnect not immediate victory");
        GameCore.Player reclaimed=w.reconnect("test-token-2");check(reclaimed!=null&&reclaimed.id==2,"reconnect same identity");
        for(int difficulty=0;difficulty<3;difficulty++)for(int match=0;match<35;match++){
            GameCore ai=new GameCore();ai.addOfflineAI(5,difficulty);
            for(int step=0;step<300;step++){ai.tick();if(ai.phase.equals("FINISHED"))break;}
            check(ai.players.size()==6,"AI max factions");
        }
        System.out.println("PASS: six-slot cap, 3v3, resources, purchases, movement, ownership, snapshot, disconnect, reconnect, 105 AI simulations");
    }
}
