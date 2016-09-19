package tst;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.codec.binary.Base64;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Scanner;
import java.util.TimeZone;

class GetThread extends Thread {
    private Date from = new Date(0);
    private String authStringEnc;

    public GetThread(String authStringEnc) {
        this.authStringEnc = authStringEnc;
    }

    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                URL url = new URL("http://localhost:8080/get?from=" + from.getTime());
                HttpURLConnection http = (HttpURLConnection) url.openConnection();
                http.setRequestProperty("Authorization", "Basic " + authStringEnc);

                if (http.getResponseCode() != 200) {
                    this.interrupt();
                    break;
                }

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int r;

                InputStream is = http.getInputStream();
                try {
                    do {
                        byte[] buf = new byte[is.available()];
                        r = is.read(buf);
                        if (r > 0)
                            bos.write(buf, 0, r);
                    } while (r > 0);

                    Gson gson = new GsonBuilder()
                            .setDateFormat("yyyy-MM-dd HH:mm:ss")
                            .create();

                    Message[] list = gson.fromJson(new String(bos.toByteArray()), Message[].class);
                    if (list != null) {
                        for (Message m : list) {
                            System.out.println(m);
                            from = m.getDate();
                        }
                    }
                } finally {
                    is.close();
                }

                Thread.sleep(1000);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }
    }
}

public class Main {
    private static boolean running = true;

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Etc/UTC"));

        Scanner scanner = new Scanner(System.in);
        try {
            while (running == true) {
                System.out.println("1 - Sing Up");
                System.out.println("2 - Sing In:");
                System.out.println("3 - Exit");
                String answ = scanner.nextLine();
                switch (answ) {
                    case "1":
                        singUp(scanner);
                        break;
                    case "2":
                        try {
                            singIn(scanner);
                        } catch (UnAutorizyException e) {
                            System.out.println("You're not authorized, please sing in or sing up if u not");
                        }
                        break;
                    case "3":
                        running = false;
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    private static void singUp(Scanner scanner) throws IOException {
        System.out.println("Enter login: ");
        String login = scanner.nextLine();
        System.out.println("Enter password: ");
        String password = scanner.nextLine();

        URL url = new URL("http://localhost:8080/singup");
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        http.setRequestMethod("POST");
        String urlParameters = "login="+login+"&password="+password;

        // Send post request
        http.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(http.getOutputStream());
        wr.writeBytes(urlParameters);
        wr.flush();
        wr.close();
        http.disconnect();
    }

    private static void singIn(Scanner scanner) throws UnAutorizyException {
        System.out.println("Enter login: ");
        String login = scanner.nextLine();
        System.out.println("Enter password: ");
        String password = scanner.nextLine();

        String authStringEnc = creatAuthCode(login, password);

        GetThread th = new GetThread(authStringEnc);
        th.setDaemon(true);
        th.start();

        while (true) {
            String text = scanner.nextLine();
            if (text.isEmpty())
                break;

            Message m = new Message();
            m.setText(text);
            m.setFrom(login);

            try {
                int res = m.send("http://localhost:8080/add", authStringEnc);
                if (res != 200) {
                    throw new UnAutorizyException();
                }
            } catch (IOException ex) {
                System.out.println("Error: " + ex.getMessage());
                return;
            }
        }
    }

    private static String creatAuthCode(String login, String password) {
        String authString = login + ":" + password;
        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
        return new String(authEncBytes);
    }
}
