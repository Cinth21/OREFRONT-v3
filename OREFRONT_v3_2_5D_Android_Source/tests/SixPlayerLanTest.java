import com.student.orefront.GameCore;
import com.student.orefront.LanRoom;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Actual TCP sockets, six identities (host + 5 independent client connections) on loopback; NOT six physical phones. */
public final class SixPlayerLanTest {
    static final class Probe implements LanRoom.Listener {
        volatile GameCore world;volatile int id=-1;volatile String error="";
        @Override public void onState(GameCore g,int playerId){world=g;id=playerId;}
        @Override public void onMessage(String msg){error=msg;}
        @Override public void onDisconnected(String msg){error=msg;}
    }
    static void check(boolean condition,String description){if(!condition)throw new AssertionError(description);}
    static void waitFor(BooleanSupplier b,String label)throws Exception{long until=System.currentTimeMillis()+7000;while(!b.getAsBoolean()&&System.currentTimeMillis()<until)Thread.sleep(35);check(b.getAsBoolean(),"TIMEOUT: "+label);}
    public static void main(String[] args)throws Exception {
        Probe host=new Probe();LanRoom.Server s=new LanRoom.Server("Host","host-identity-0000",host);
        ArrayList<LanRoom.Client> clients=new ArrayList<>();ArrayList<Probe> probes=new ArrayList<>();
        try {
            for(int i=1;i<=5;i++){
                Probe p=new Probe();LanRoom.Client c=new LanRoom.Client("127.0.0.1",s.code(),"Commander"+i,"token-six-"+i,p);
                clients.add(c);probes.add(p);
            }
            waitFor(()->s.snapshot().connectedHumans()==6,"six connected humans");
            for(int i=0;i<5;i++){final int j=i;waitFor(()->probes.get(j).id==j+1,"correct player IDs");}
            boolean rejected=false;
            try{LanRoom.Client seventh=new LanRoom.Client("127.0.0.1",s.code(),"Seventh","no-more-slots",new Probe());seventh.close();}catch(IOException expected){rejected=true;}
            check(rejected,"seventh player must be rejected");
            check(s.command("CFG|MODE|3v3"),"host picks 3v3");
            check(s.command("READY|1"),"host ready");
            for(LanRoom.Client c:clients)check(c.command("READY|1"),"client ready");
            waitFor(()->{GameCore w=s.snapshot();for(GameCore.Player p:w.players.values())if(!p.ready)return false;return true;},"all six ready");
            check(s.command("START"),"start match with six separate identities");
            waitFor(()->probes.stream().allMatch(p->p.world!=null&&p.world.phase.equals("PLAY")),"all clients playing");
            check(s.command("BUILD|BARRACKS|2|3"),"host builds barracks");
            check(clients.get(0).command("BUILD|MINE|14|9"),"client submits build command");
            waitFor(()->s.snapshot().buildings.size()==2,"two players built structures");
            check(clients.get(1).command("BUILD|BARRACKS|3|9"),"another client builds barracks");
            waitFor(()->s.snapshot().buildings.size()==3,"third build");
            check(clients.get(1).command("TRAIN|SOLDIER"),"third player requests soldier");
            waitFor(()->s.snapshot().units.size()>0,"purchase processed");
            GameCore snap=s.snapshot();GameCore.Unit u=snap.units.get(0);
            check(u.owner==2,"unit belongs to caller, not host");
            check(clients.get(1).command("MOVE|"+u.id+"|5|8"),"unit move requested");
            waitFor(()->{GameCore w=s.snapshot();return w.units.get(0).tx==5;},"unit moved by correct player");
            waitFor(()->probes.stream().allMatch(p->p.world!=null&&p.world.buildings.size()==3&&p.world.units.size()>0),"all clients share buildings and troops");
            GameCore base=s.snapshot();int retained=base.player(3).baseHP;
            clients.get(2).close();
            waitFor(()->!s.snapshot().player(3).connected,"disconnect recorded");
            check(s.snapshot().player(3).baseHP==retained,"base retained");
            check(s.command("AI_TAKEOVER|3|1"),"host delegates disconnected faction to AI");
            check(s.snapshot().player(3).ai,"AI takeover enabled");
            Probe reunited=new Probe();LanRoom.Client restored=new LanRoom.Client("127.0.0.1",s.code(),"Commander3","token-six-3",reunited);
            waitFor(()->reunited.id==3&&s.snapshot().player(3).connected,"reconnect preserves slot");
            check(!s.snapshot().player(3).ai,"human regained control");
            restored.close();
            check(s.snapshot().phase.equals("PLAY"),"disconnect never automatically ends game");
            System.out.println("PASS: host + five real TCP clients, room code, max six, ready, 3v3, state sync, buildings, purchases, movement, disconnect, AI takeover, reconnect");
        } finally {for(LanRoom.Client c:clients)c.close();s.close();}
    }
}
