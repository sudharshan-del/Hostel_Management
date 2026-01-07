import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class MessServer {
    private static final String SHARED_FILE = "mess_stats.dat";
    private static final int MEM_SIZE = 12; 
    
    // Memory Storage
    private static Map<String, String> menuStore = new HashMap<>();

    public static void main(String[] args) throws IOException {
        initializeSharedMemory();
        initializeDefaultMenu(); // <--- This now loads DIFFERENT food for each day

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        
        server.createContext("/submit", new FeedbackHandler());
        server.createContext("/stats", new StatsHandler());
        server.createContext("/menu", new MenuHandler());
        server.createContext("/admin/update", new AdminUpdateHandler());
        
        server.setExecutor(null);
        System.out.println("Backend Server started on port 8000...");
        server.start();
    }

    // --- FIXED: LOAD DIFFERENT MENUS FOR DIFFERENT DAYS ---
    private static void initializeDefaultMenu() {
        String bfStaples = " + Milk/Tea, Bread & Jam";
        String mealStaples = " + Rice, Curd, Pickle";

        // 1. MONDAY MENU
        menuStore.put("MONDAY_BREAKFAST", menuObj("Idli Sambar" + bfStaples, "65g", "12g", "14g"));
        menuStore.put("MONDAY_LUNCH", menuObj("Dal Makhani" + mealStaples, "80g", "15g", "18g"));
        menuStore.put("MONDAY_SNACKS", menuObj("Samosa", "25g", "10g", "3g"));
        menuStore.put("MONDAY_DINNER", menuObj("Paneer Butter Masala" + mealStaples, "75g", "22g", "20g"));

        // 2. TUESDAY, THURSDAY, SATURDAY MENU
        String[] altDays = {"TUESDAY", "THURSDAY", "SATURDAY"};
        for (String d : altDays) {
            menuStore.put(d + "_BREAKFAST", menuObj("Aloo Paratha" + bfStaples, "75g", "18g", "12g"));
            menuStore.put(d + "_LUNCH", menuObj("Chana Masala" + mealStaples, "80g", "12g", "15g"));
            menuStore.put(d + "_SNACKS", menuObj("Tea & Bun", "30g", "4g", "4g"));
            menuStore.put(d + "_DINNER", menuObj("Mix Veg Curry" + mealStaples, "70g", "14g", "10g"));
        }

        // 3. WEDNESDAY, FRIDAY MENU
        String[] wedFri = {"WEDNESDAY", "FRIDAY"};
        for (String d : wedFri) {
            menuStore.put(d + "_BREAKFAST", menuObj("Poha" + bfStaples, "70g", "14g", "10g"));
            menuStore.put(d + "_LUNCH", menuObj("Rajma Masala" + mealStaples, "85g", "12g", "16g"));
            menuStore.put(d + "_SNACKS", menuObj("Biscuits", "30g", "12g", "2g"));
            menuStore.put(d + "_DINNER", menuObj("Veg Biryani" + mealStaples, "90g", "18g", "12g"));
        }

        // 4. SUNDAY SPECIAL
        menuStore.put("SUNDAY_BREAKFAST", menuObj("Masala Dosa" + bfStaples, "80g", "18g", "10g"));
        menuStore.put("SUNDAY_LUNCH", menuObj("Chicken Biryani" + mealStaples, "100g", "25g", "30g"));
        menuStore.put("SUNDAY_SNACKS", menuObj("Cream Cake", "40g", "15g", "4g"));
        menuStore.put("SUNDAY_DINNER", menuObj("Aloo Gobi" + mealStaples, "70g", "10g", "12g"));
    }

    private static String menuObj(String item, String carbs, String fat, String protein) {
        return String.format(
            "{\"item\": \"%s\", \"carbs\": \"%s\", \"fat\": \"%s\", \"protein\": \"%s\"}",
            item, carbs, fat, protein
        );
    }

    // --- STANDARD HANDLERS ---
    private static void initializeSharedMemory() {
        File file = new File(SHARED_FILE);
        if (!file.exists()) {
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                raf.setLength(MEM_SIZE);
                raf.writeInt(0); raf.writeInt(0); raf.writeInt(0);
            } catch (IOException e) { e.printStackTrace(); }
        }
    }
    
    private static void enableCORS(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    static class FeedbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            enableCORS(exchange);
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                Scanner s = new Scanner(is, StandardCharsets.UTF_8.name());
                String body = s.hasNext() ? s.next() : "";
                int voteType = Integer.parseInt(body.trim()); 
                updateSharedMemory(voteType);
                String response = "Vote Received";
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }

    static class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            enableCORS(exchange);
            if ("GET".equals(exchange.getRequestMethod())) {
                String response = readSharedMemory();
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }

    static class MenuHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            enableCORS(exchange);
            if ("GET".equals(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                String dateStr = query.split("=")[1];
                LocalDate date = LocalDate.parse(dateStr);
                String day = date.getDayOfWeek().toString(); 
                
                String jsonResponse = String.format(
                    "{\"breakfast\": %s, \"lunch\": %s, \"snacks\": %s, \"dinner\": %s}",
                    menuStore.get(day + "_BREAKFAST"),
                    menuStore.get(day + "_LUNCH"),
                    menuStore.get(day + "_SNACKS"),
                    menuStore.get(day + "_DINNER")
                );
                
                exchange.sendResponseHeaders(200, jsonResponse.length());
                OutputStream os = exchange.getResponseBody();
                os.write(jsonResponse.getBytes());
                os.close();
            }
        }
    }

    static class AdminUpdateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            enableCORS(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                Scanner s = new Scanner(is, StandardCharsets.UTF_8.name());
                s.useDelimiter("\\A");
                String body = s.hasNext() ? s.next() : "";
                
                String[] parts = body.split("\\|");
                if(parts.length == 6) {
                    String key = parts[0] + "_" + parts[1]; 
                    String newVal = menuObj(parts[2], parts[3], parts[4], parts[5]);
                    menuStore.put(key, newVal);
                    System.out.println("Admin updated: " + key);
                }

                String response = "Updated";
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }

    private static void updateSharedMemory(int offsetIndex) {
        try (FileChannel channel = FileChannel.open(new File(SHARED_FILE).toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            try (FileLock lock = channel.lock()) { 
                MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, MEM_SIZE);
                int currentVal = buffer.getInt(offsetIndex * 4);
                buffer.putInt(offsetIndex * 4, currentVal + 1);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static String readSharedMemory() {
        try (FileChannel channel = FileChannel.open(new File(SHARED_FILE).toPath(), StandardOpenOption.READ)) {
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, MEM_SIZE);
            return "{\"good\": " + buffer.getInt(0) + ", \"avg\": " + buffer.getInt(4) + ", \"poor\": " + buffer.getInt(8) + "}";
        } catch (IOException e) { return "{}"; }
    }
}