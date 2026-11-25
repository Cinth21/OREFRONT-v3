package com.student.orefront;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Real LAN sockets: one authoritative Android phone, up to five remote human sockets. */
public final class LanRoom {
    public static final int PORT=38417, DISCOVERY_PORT=38418;
    public interface Listener {
        void onState(GameCore state,int localPlayerId);
        void onMessage(String message);
        void onDisconnected(String reason);
    }
    static String enc(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
    static String dec(String s){return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);}
    public static String localIP(){
        try{Enumeration<NetworkInterface> faces=NetworkInterface.getNetworkInterfaces();
            while(faces.hasMoreElements()){NetworkInterface f=faces.nextElement();if(!f.isUp()||f.isLoopback())continue;
                Enumeration<InetAddress> ips=f.getInetAddresses();
                while(ips.hasMoreElements()){InetAddress a=ips.nextElement();
                    if(a instanceof Inet4Address&&!a.isLoopbackAddress()&&a.isSiteLocalAddress())return a.getHostAddress();
                }
            }
        }catch(Exception ignored){}
        return "See Wi-Fi / hotspot network details";
    }
    public static final class RoomInfo {
        public final String code,host,name;public final int people,maximum;public final boolean started;
        RoomInfo(String code,String host,String name,int people,int maximum,boolean started){this.code=code;this.host=host;this.name=name;this.people=people;this.maximum=maximum;this.started=started;}
        public String toString(){return name+"  ["+code+"]  "+people+"/"+maximum+(started?" • playing":" • waiting")+"  • "+host;}
    }
    public static List<RoomInfo> search(int timeoutMs){
        LinkedHashMap<String,RoomInfo> found=new LinkedHashMap<>();
        try(DatagramSocket socket=new DatagramSocket()){
            socket.setBroadcast(true);socket.setSoTimeout(180);
            byte[] query="FIND_OREFRONT".getBytes(StandardCharsets.US_ASCII);
            ArrayList<InetAddress> targets=new ArrayList<>();targets.add(InetAddress.getByName("255.255.255.255"));
            try{Enumeration<NetworkInterface> faces=NetworkInterface.getNetworkInterfaces();
                while(faces.hasMoreElements()){NetworkInterface f=faces.nextElement();if(!f.isUp()||f.isLoopback())continue;
                    for(InterfaceAddress a:f.getInterfaceAddresses())if(a.getBroadcast()!=null)targets.add(a.getBroadcast());
                }
            }catch(Exception ignored){}
            for(InetAddress target:targets)try{socket.send(new DatagramPacket(query,query.length,target,DISCOVERY_PORT));}catch(Exception ignored){}
            long until=System.currentTimeMillis()+Math.max(350,timeoutMs);
            while(System.currentTimeMillis()<until){try{byte[] buf=new byte[384];DatagramPacket packet=new DatagramPacket(buf,buf.length);socket.receive(packet);
                String s=new String(packet.getData(),packet.getOffset(),packet.getLength(),StandardCharsets.UTF_8);
                String[] c=s.split("\\|",-1);
                if(c.length==6&&c[0].equals("OREFRONT")&&c[1].matches("\\d{6}")){
                    RoomInfo room=new RoomInfo(c[1],packet.getAddress().getHostAddress(),GameCore.clean(c[5]),Integer.parseInt(c[2]),Integer.parseInt(c[3]),c[4].equals("1"));
                    found.put(room.host+":"+room.code,room);
                }
            }catch(SocketTimeoutException ignored){} }
        }catch(Exception ignored){}
        return new ArrayList<>(found.values());
    }
    public static final class Server implements AutoCloseable {
        private final Object lock=new Object();
        private final GameCore world=new GameCore();
        private final Map<Integer,Peer> peers=new HashMap<>();
        private final Listener hostListener;
        private final ServerSocket server;
        private final DatagramSocket discover;
        private volatile boolean running=true;
        private final ScheduledExecutorService clock=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"orefront-clock");t.setDaemon(true);return t;});
        private String lastSnapshot="";
        private int lastPublishedRevision=-1,lastPublishedSecond=-1;
        private final int roomCode;
        public Server(String hostname,String hostToken,Listener listener)throws IOException{
            hostListener=listener;
            server=new ServerSocket();server.setReuseAddress(true);server.bind(new InetSocketAddress(PORT));
            DatagramSocket udp=null;try{udp=new DatagramSocket(null);udp.setReuseAddress(true);udp.bind(new InetSocketAddress(DISCOVERY_PORT));}catch(Exception ignored){}discover=udp;
            roomCode=100000+new java.security.SecureRandom().nextInt(900000);
            world.roomCode=String.valueOf(roomCode);
            GameCore.Player host=world.addHuman(hostname,hostToken);
            if(host==null||host.id!=0)throw new IOException("Cannot initialize host");
            new Thread(this::acceptLoop,"orefront-accept").start();
            if(discover!=null)new Thread(this::discoveryLoop,"orefront-discovery").start();
            clock.scheduleAtFixedRate(()->{synchronized(lock){if(!running)return;world.tick();publish();}},500,500,TimeUnit.MILLISECONDS);
            synchronized(lock){publish();}
        }
        public String code(){return world.roomCode;}
        public String ip(){return localIP();}
        public GameCore snapshot(){synchronized(lock){return GameCore.decode(world.encode());}}
        public boolean command(String message){synchronized(lock){return process(0,message,true);}}
        private boolean process(int id,String message,boolean host){
            if(message==null||message.length()>160)return false;
            String[] c=message.split("\\|",-1);boolean success=false;
            try{switch(c[0]){
                case "READY":success=world.setReady(id,c.length>1&&c[1].equals("1"));break;
                case "CFG":if(host&&c.length==3)success=world.setConfig(c[1],c[2]);break;
                case "TEAM":if(host&&c.length==3)success=world.setTeam(Integer.parseInt(c[1]),Integer.parseInt(c[2]));break;
                case "FACTION":if(host&&c.length==3)success=world.setFaction(Integer.parseInt(c[1]),Integer.parseInt(c[2]));break;
                case "START":if(host)success=world.start();break;
                case "AI_TAKEOVER":if(host&&c.length>=3){GameCore.Player p=world.player(Integer.parseInt(c[1]));if(p!=null&&!p.connected&&world.phase.equals("PLAY")){p.ai=c[2].equals("1");world.revision++;success=true;}}break;
                case "BUILD":if(c.length>=4)success=world.build(id,c[1],Integer.parseInt(c[2]),Integer.parseInt(c[3]));break;
                case "UPGRADE":if(c.length>=2)success=world.upgrade(id,Integer.parseInt(c[1]));break;
                case "TRAIN":if(c.length>=2)success=world.recruit(id,c[1]);break;
                case "MOVE":if(c.length>=4)success=world.move(id,Integer.parseInt(c[1]),Integer.parseInt(c[2]),Integer.parseInt(c[3]));break;
                case "LEAVE":if(!host){GameCore.Player p=world.player(id);if(p!=null)world.disconnect(id,true);success=true;}break;
                default:break;
            }}catch(Exception ignored){}
            if(success){publish();}else{
                String reason=message.startsWith("START")?world.startCheck():"Action denied: invalid order, resources, position or permissions";
                if(host){if(hostListener!=null)hostListener.onMessage(reason);}else{Peer p=peers.get(id);if(p!=null)p.send("NOTICE|"+enc(reason));}
            }
            return success;
        }
        private void acceptLoop(){while(running){try{
            Socket s=server.accept();s.setTcpNoDelay(true);s.setSoTimeout(7000);
            new Thread(()->acceptPeer(s),"orefront-handshake").start();
        }catch(IOException e){if(running&&hostListener!=null)hostListener.onMessage("LAN host error: "+e.getMessage());}}
        }
        private void acceptPeer(Socket s){Peer peer=null;
            try{
                DataInputStream input=new DataInputStream(new BufferedInputStream(s.getInputStream()));
                DataOutputStream output=new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                String[] hello=input.readUTF().split("\\|",-1);
                if(hello.length!=4||!hello[0].equals("HELLO")||!hello[1].equals(world.roomCode)){
                    output.writeUTF("ERROR|"+enc("Wrong room code"));output.flush();s.close();return;
                }
                String token=dec(hello[2]),name=GameCore.clean(dec(hello[3]));
                if(token.length()<8||token.length()>80)throw new IOException("Invalid reconnect token");
                int id;
                synchronized(lock){
                    GameCore.Player p=world.reconnect(token);
                    if(p==null)p=world.addHuman(name,token);
                    if(p==null){output.writeUTF("ERROR|"+enc("Room full or match already started"));output.flush();s.close();return;}
                    id=p.id;
                    Peer old=peers.get(id);if(old!=null)old.close();
                    peer=new Peer(s,id,input,output);peers.put(id,peer);
                    output.writeUTF("WELCOME|"+id+"|"+world.roomCode);output.flush();
                    publish();
                }
                s.setSoTimeout(0);peer.start();
            }catch(Exception e){try{s.close();}catch(Exception ignored){}}
        }
        private void peerLeft(Peer peer){synchronized(lock){if(peers.get(peer.id)!=peer)return;peers.remove(peer.id);world.disconnect(peer.id,false);GameCore.Player player=world.player(peer.id);if(player!=null)world.lastEvent=player.name+" disconnected (base preserved)";publish();}}
        private void discoveryLoop(){if(discover==null)return;
            while(running){try{byte[] buf=new byte[256];DatagramPacket p=new DatagramPacket(buf,buf.length);discover.receive(p);
                String query=new String(p.getData(),p.getOffset(),p.getLength(),StandardCharsets.US_ASCII);
                if(!query.equals("FIND_OREFRONT"))continue;
                String msg;
                synchronized(lock){msg="OREFRONT|"+world.roomCode+"|"+world.connectedHumans()+"|"+world.maxPlayers+"|"+(world.phase.equals("LOBBY")?"0":"1")+"|"+GameCore.clean(world.player(0).name);}
                byte[] reply=msg.getBytes(StandardCharsets.US_ASCII);discover.send(new DatagramPacket(reply,reply.length,p.getAddress(),p.getPort()));
            }catch(Exception ignored){}}
        }
        private void publish(){
            boolean changed=world.revision!=lastPublishedRevision;
            int second=world.elapsedTicks/2;
            boolean timerChanged=second!=lastPublishedSecond;
            if(!changed&&!timerChanged)return;
            lastPublishedRevision=world.revision;lastPublishedSecond=second;
            if(!changed){
                for(Peer p:new ArrayList<>(peers.values()))p.send("TICK|"+world.elapsedTicks);
                return;
            }
            String snapshot=world.encode();lastSnapshot=snapshot;
            GameCore copy=GameCore.decode(snapshot);
            if(hostListener!=null)try{hostListener.onState(copy,0);}catch(Exception ignored){}
            String msg="STATE|"+enc(snapshot);
            for(Peer p:new ArrayList<>(peers.values())){p.send(msg);p.send("INTEL|"+enc(world.intelligence(p.id)));}
        }
        private final class Peer {
            final Socket socket;final int id;final DataInputStream in;final DataOutputStream out;
            final LinkedBlockingDeque<String> queue=new LinkedBlockingDeque<>(4);
            volatile boolean alive=true;
            Peer(Socket socket,int id,DataInputStream in,DataOutputStream out){this.socket=socket;this.id=id;this.in=in;this.out=out;}
            void send(String s){if(!alive)return;while(!queue.offerLast(s))queue.pollFirst();}
            void start(){
                Thread writer=new Thread(()->{try{while(alive){String m=queue.poll(3,TimeUnit.SECONDS);if(m!=null){synchronized(out){out.writeUTF(m);out.flush();}}}}catch(Exception ignored){close();}},"orefront-writer-"+id);writer.setDaemon(true);writer.start();
                try{while(alive){String command=in.readUTF();synchronized(lock){if(peers.get(id)==this)process(id,command,false);}}}catch(Exception ignored){}finally{close();peerLeft(this);}
            }
            void close(){alive=false;try{socket.close();}catch(Exception ignored){}}
        }
        @Override public void close(){running=false;clock.shutdownNow();try{server.close();}catch(Exception ignored){}if(discover!=null)discover.close();synchronized(lock){for(Peer p:peers.values())p.close();peers.clear();}}
    }
    public static final class Client implements AutoCloseable {
        private final Socket socket;private final DataInputStream input;private final DataOutputStream output;
        private final Listener listener;private volatile boolean running=true;
        private int id=-1;
        private GameCore lastState;
        public final String code;
        public Client(String ip,String code,String name,String token,Listener listener)throws IOException{
            this.listener=listener;this.code=code;
            socket=new Socket();socket.connect(new InetSocketAddress(ip,PORT),4500);socket.setTcpNoDelay(true);socket.setSoTimeout(6500);
            input=new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            output=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            synchronized(output){output.writeUTF("HELLO|"+code+"|"+enc(token)+"|"+enc(name));output.flush();}
            String resp=input.readUTF();String[] c=resp.split("\\|",-1);
            if(c[0].equals("ERROR")){socket.close();throw new IOException(c.length>1?dec(c[1]):"Join failed");}
            if(c.length!=3||!c[0].equals("WELCOME")){socket.close();throw new IOException("Invalid host response");}
            id=Integer.parseInt(c[1]);socket.setSoTimeout(0);
            Thread receiver=new Thread(this::receive,"orefront-client-reader");receiver.setDaemon(true);receiver.start();
        }
        public int playerId(){return id;}
        private void receive(){try{while(running){String msg=input.readUTF();String[] c=msg.split("\\|",2);
            if(c.length!=2)continue;
            if(c[0].equals("STATE")){lastState=GameCore.decode(dec(c[1]));listener.onState(lastState,id);}
            else if(c[0].equals("TICK")&&lastState!=null){try{lastState.elapsedTicks=Integer.parseInt(c[1]);listener.onState(lastState,id);}catch(Exception ignored){}}
            else if(c[0].equals("NOTICE")||c[0].equals("INTEL"))listener.onMessage(dec(c[1]));
        }}catch(Exception e){if(running)listener.onDisconnected("Host disconnected or LAN connection lost");}finally{close();}}
        public boolean command(String m){if(!running)return false;try{synchronized(output){output.writeUTF(m);output.flush();}return true;}catch(Exception e){close();listener.onDisconnected("LAN connection lost");return false;}}
        @Override public void close(){running=false;try{socket.close();}catch(Exception ignored){}}
    }
}
