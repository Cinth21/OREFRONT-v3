import com.student.orefront.GameCore;
public final class EnhancedFeatureTest {
    static void check(boolean pass,String msg){if(!pass)throw new AssertionError(msg);}
    public static void main(String[] args){
        GameCore w=new GameCore();
        for(int i=0;i<2;i++){
            check(w.addHuman("Commander"+i,"enhanced-token-"+i)!=null,"join");
            check(w.setReady(i,true),"ready");
        }
        check(w.setConfig("MODE","1v1"),"mode");
        check(w.start(),"start");
        check(w.build(0,"TURRET",3,2),"defense tower built near HQ");
        check(!w.build(1,"TURRET",3,3),"no enemy build close to another HQ");
        check(w.recruit(1,"HERO"),"enemy hero available");
        GameCore.Unit enemy=w.units.get(0);
        enemy.x=3; enemy.y=3;enemy.tx=3;enemy.ty=3;
        int hp=enemy.hp;
        w.tick();w.tick();
        check(enemy.hp<hp,"defensive turret dealt host-authoritative combat damage");
        String wire=w.encode();GameCore copy=GameCore.decode(wire);
        check(copy.buildings.stream().anyMatch(b->b.type.equals("TURRET")),"turret synchronized in snapshot");
        check(copy.player(1).energy==w.player(1).energy,"resources synchronize");
        check(!w.move(0,enemy.id,7,7),"another player's unit is protected");
        check(w.intelligence(0).startsWith("INTEL:"),"player-specific intelligence");
        System.out.println("PASS: turret construction, defensive fire, synchronized structure, resources, ownership, AI insights");
    }
}
