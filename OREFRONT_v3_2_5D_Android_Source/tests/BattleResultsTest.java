import com.student.orefront.GameCore;
import java.util.*;
public final class BattleResultsTest {
    static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
    public static void main(String[] args){
        GameCore w=new GameCore();
        for(int i=0;i<6;i++){GameCore.Player p=w.addHuman("Commander"+i,"secureToken"+i);p.ready=true;}
        w.setConfig("MODE","3v3");check(w.start(),"3v3 starts");
        for(GameCore.Player p:w.players.values())if(p.team==1)p.baseHP=0;
        w.tick();
        check(w.phase.equals("FINISHED"),"Combat victory phase");check(w.winningTeam==0,"3v3 team 0 wins");
        GameCore mirror=GameCore.decode(w.encode());
        check(mirror.phase.equals("FINISHED"),"finish snapshot");check(mirror.winningTeam==0,"victory sync");
        GameCore timed=new GameCore();
        timed.addOfflineAI(2,2);timed.elapsedTicks=timed.matchMinutes*120-1;
        timed.tick();
        check(timed.phase.equals("FINISHED"),"timer resolves results");
        System.out.println("PASS: combat victory, team result state replication, match timer");
    }
}
