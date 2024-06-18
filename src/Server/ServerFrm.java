package Server;

import Entity.*;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.table.DefaultTableModel;
import Service.*;
import java.io.IOException;
import static java.lang.Thread.sleep;
import java.util.Properties;
import java.util.Random;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class ServerFrm extends javax.swing.JFrame {

    List<User> _lstUsers;
    Connection _conn;
    ServerSocket _server;
    ClientService _service;
    Socket _socket;
    String request = "";
    List<User> _lstOnline;
    String feedBack;

    public ServerFrm() {
        _lstOnline = new ArrayList<>();
        _lstUsers = new ArrayList<>();
        _conn = getConnection();
        initComponents();
        
        startingConnect();
        getDataUsers();
        loadTableUsers();
        setLBL();
        
        this.setLocationRelativeTo(null);
        this.setResizable(false);
        this.setSize(650, 550);
    }

    Connection getConnection() {
        try {
            return DriverManager.getConnection("jdbc:mysql://localhost:3306/messenger_sk", "root", "Nguyenhong24@");
        } catch (Exception e) {
        }
        return null;
    }

    void loadTableUsers() {
        DefaultTableModel def = (DefaultTableModel) tbl_listUsers.getModel();
        new Thread() {
            @Override
            public void run() {
                updateListUserOnline();
                while (!_server.isClosed()) {
                    def.setRowCount(0);
                    int i = 1;
                    for (User user : _lstUsers) {
                        def.addRow(new Object[]{
                            i++, user.getId(), user.getFullName(), checkOnline(user.getUserName()) ? "Online" : "Offline"
                        });
                    }
                    try {
                        sleep(500);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(ServerFrm.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }

            private boolean checkOnline(String userName) {
                for (ClientService clientService : ClientService.lstSocket) {
                    if (clientService.getName().equals(userName)) {
                        return true;
                    }
                }
                return false;
            }

        }.start();
        tbl_listUsers.setFocusable(false);
        tbl_listUsers.setEnabled(false);
    }

    void closeServer() {
        try {
            if (_conn != null) {
                _conn.close();
            }
            if (_socket != null) {
                _socket.close();
            }
            if (_server != null) {
                _server.close();
            }
        } catch (Exception ex) {
        }
    }

    void startingConnect() {
        try {
            _server = new ServerSocket(6666);
        } catch (Exception e) {
            System.out.println("Server is not running!");
        }
        Thread thr = new Thread() {
            @Override
            public void run() {
                while (!_server.isClosed()) {
                    try {
                        _socket = _server.accept();
                        _service = new ClientService(_socket, "client" + ClientService.lstSocket.size()) {
                            @Override
                            public void run() {
                                try {
                                    while (_service.getSocket().isConnected()) {
                                        String request = this.getIn().readLine();
                                        processRequest(request);
                                        sleep(500);
                                    }
                                } catch (Exception ex) {
                                    this.closeEverything();
                                }
                            }
                        };
                        Thread thread = new Thread(_service);
                        thread.start();
                    } catch (Exception ex) {
                        closeServer();
                    }
                }
                try {
                    _server.close();
                } catch (IOException ex) {
                    Logger.getLogger(ServerFrm.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        thr.start();
    }

    void sendListOnline() {
        new Thread() {
            @Override
            public void run() {
                while (_service.getSocket().isConnected()) {
                    for (int i = 0; i < ClientService.lstSocket.size(); i++) {
                        ClientService client = ClientService.lstSocket.get(i);
                        if (_lstOnline.size() > 0 && !client.getSocket().isClosed() && client.isIsValidUser()) {
                            String feedBack = "listOnline ";
                            for (User user : _lstOnline) {
                                feedBack += user.getUserName() + " " + user.getFullName() + ",";
                            }
                            feedBack = feedBack + " " + client.getName();
                            client.getOut().println(feedBack);
                            client.getOut().flush();
                        }
                    }
                }
                try {
                    sleep(1000);
                } catch (Exception e) {
                }
            }

        }.start();
    }

    void processRequest(String request) {
        if (request.startsWith("login")) {
            logging(request);
        } else if (request.startsWith("checkUserName")) {
            checkUserName(request);
        } else if (request.startsWith("changePass")) {
            changePassword(request);
        } else if (request.startsWith("getDialogue")) {
            sendDialogue(request);
        } else if (request.startsWith("send")) {
            updateNewMessage(request);
        }
    }

private void logging(String request) {
    String[] ar = request.split("\\s");
    String userName = ar[1];
    String hashedPassword = ar[2];
    String clientName = request.substring(request.lastIndexOf(" ") + 1);
    
    // Sử dụng hàm checkLogin để kiểm tra tên người dùng và mật khẩu đã băm
    boolean loginSuccessful = checkLogin(userName, hashedPassword);

    String result = (loginSuccessful ? "loginOK " : "loginNotOk ") + userName + " " + clientName;
    if (loginSuccessful) {
        _service.setIsValidUser(true);
        _service.setName(userName);
    }
    _service.getOut().println(result);
    _service.getOut().flush();
}

private void changePassword(String request) {
    String[] ar = request.split("\\s");
    String userName = ar[1];
    String hashedPassword = ar[2];
    
    for (User user : _lstUsers) {
        if (user.getUserName().equals(userName)) {
            user.setPassWord(hashedPassword); // Cập nhật mật khẩu đã băm
            _service.getOut().println("PSchanged");
            _service.getOut().flush();
            return;
        }
    }
    _service.getOut().println("NoUser");
    _service.getOut().flush();
}


    private boolean checkUserName(String request) {
        String ar[] = request.split("\\s");
        String input = ar[1];
        for (User user : _lstUsers) {
            if (user.getUserName().equals(input)) {
                return true;
            }
        }
        return false;
    }
    boolean checkLogin(String userName, String hashedPassword) {
    for (User user : _lstUsers) {
        if (user.getUserName().equals(userName) && user.getPassWord().equals(hashedPassword)) {
            return true;
        }
    }
    return false;
}    

    void updateListUserOnline() {
        new Thread() {
            @Override
            public void run() {
                while (!_server.isClosed()) {
                    List<User> lst = new ArrayList<>();
                    int count = 0;
                    for (ClientService client : ClientService.lstSocket) {
                        if (client.isIsValidUser()) {
                            count++;
                            lst.add(getUserByUserName(client.getName()));
                        }
                    }
                    lbl_numUsers1.setText(String.valueOf(count));
                    _lstOnline = lst;
                    for (int i = 0; i < ClientService.lstSocket.size(); i++) {
                        ClientService client = ClientService.lstSocket.get(i);
                        if (client.isIsValidUser()) {
                            String feedBack = "listOnline ";
                            for (User user : _lstOnline) {
                                feedBack += user.getUserName() + " " + user.getFullName() + ",";
                            }
                            feedBack = feedBack + " " + client.getName();
                            client.getOut().println(feedBack);
                            client.getOut().flush();
                        }
                    }

                    try {
                        sleep(1000);
                    } catch (Exception e) {
                    }
                }
            }

        }.start();

    }

    private User getUserByUserName(String userName) {
        for (User user : _lstUsers) {
            if (user.getUserName().equals(userName)) {
                return user;
            }
        }
        return null;
    }
//
    void setLBL() {
        try {
              InetAddress ia = InetAddress.getLocalHost();
             lbl_localHost.setText(ia.getHostAddress());
              lbl_port.setText("6666");
            

        } catch (Exception ex) {
            Logger.getLogger(ServerFrm.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    void getDataUsers() {
        try {
            _lstUsers.clear();
            ResultSet rs = _conn.prepareStatement("SELECT * FROM USERS").executeQuery();
            while (rs.next()) {
                _lstUsers.add(new User(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)));
            }
            rs.close();
        } catch (Exception e) {
            closeServer();
        }
    }

    private String generateOTP() {
        String otp = "";
        Random random = new Random();
        while (otp.length() < 6) {
            otp += (random.nextInt(8) + 1);
        }
        return otp;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        tbl_listUsers = new javax.swing.JTable();
        pnl_infor = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        lbl_port = new javax.swing.JLabel();
        lbl_localHost = new javax.swing.JLabel();
        lbl_numUsers1 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setBackground(new java.awt.Color(255, 204, 255));
        getContentPane().setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel1.setBackground(new java.awt.Color(255, 0, 102));
        jLabel1.setFont(new java.awt.Font("Times New Roman", 2, 24)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(0, 0, 0));
        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel1.setText("Server");
        getContentPane().add(jLabel1, new org.netbeans.lib.awtextra.AbsoluteConstraints(390, 30, 220, 40));

        tbl_listUsers.setBackground(new java.awt.Color(255, 204, 204));
        tbl_listUsers.setFont(new java.awt.Font("Times New Roman", 2, 14)); // NOI18N
        tbl_listUsers.setForeground(new java.awt.Color(0, 0, 0));
        tbl_listUsers.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "SI", "ID", "FullName", "Status"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        tbl_listUsers.setGridColor(new java.awt.Color(255, 204, 255));
        tbl_listUsers.setSelectionBackground(new java.awt.Color(255, 204, 255));
        tbl_listUsers.setSelectionForeground(new java.awt.Color(255, 204, 255));
        tbl_listUsers.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(tbl_listUsers);
        if (tbl_listUsers.getColumnModel().getColumnCount() > 0) {
            tbl_listUsers.getColumnModel().getColumn(0).setResizable(false);
            tbl_listUsers.getColumnModel().getColumn(0).setPreferredWidth(20);
            tbl_listUsers.getColumnModel().getColumn(1).setResizable(false);
            tbl_listUsers.getColumnModel().getColumn(1).setPreferredWidth(50);
            tbl_listUsers.getColumnModel().getColumn(2).setResizable(false);
            tbl_listUsers.getColumnModel().getColumn(2).setPreferredWidth(200);
            tbl_listUsers.getColumnModel().getColumn(3).setResizable(false);
            tbl_listUsers.getColumnModel().getColumn(3).setPreferredWidth(200);
        }

        getContentPane().add(jScrollPane1, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 280, 610, 190));

        pnl_infor.setBorder(javax.swing.BorderFactory.createTitledBorder("Information"));
        pnl_infor.setForeground(new java.awt.Color(255, 255, 255));
        pnl_infor.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel2.setFont(new java.awt.Font("Times New Roman", 2, 18)); // NOI18N
        jLabel2.setForeground(new java.awt.Color(0, 0, 0));
        jLabel2.setText("Port");
        pnl_infor.add(jLabel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 160, 90, 30));

        jLabel3.setFont(new java.awt.Font("Times New Roman", 2, 18)); // NOI18N
        jLabel3.setForeground(new java.awt.Color(0, 0, 0));
        jLabel3.setText("Localhost:");
        pnl_infor.add(jLabel3, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 40, 90, 30));

        jLabel4.setFont(new java.awt.Font("Times New Roman", 2, 18)); // NOI18N
        jLabel4.setForeground(new java.awt.Color(0, 0, 0));
        jLabel4.setText("Online:");
        pnl_infor.add(jLabel4, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 100, 90, 30));

        lbl_port.setFont(new java.awt.Font("Times New Roman", 2, 18)); // NOI18N
        lbl_port.setForeground(new java.awt.Color(0, 0, 0));
        pnl_infor.add(lbl_port, new org.netbeans.lib.awtextra.AbsoluteConstraints(130, 160, 180, 30));

        lbl_localHost.setFont(new java.awt.Font("Times New Roman", 2, 18)); // NOI18N
        lbl_localHost.setForeground(new java.awt.Color(0, 0, 0));
        pnl_infor.add(lbl_localHost, new org.netbeans.lib.awtextra.AbsoluteConstraints(140, 40, 170, 30));

        lbl_numUsers1.setFont(new java.awt.Font("Times New Roman", 2, 18)); // NOI18N
        lbl_numUsers1.setForeground(new java.awt.Color(0, 0, 0));
        pnl_infor.add(lbl_numUsers1, new org.netbeans.lib.awtextra.AbsoluteConstraints(140, 100, 180, 30));

        jLabel8.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/hinhnen.jpg"))); // NOI18N
        pnl_infor.add(jLabel8, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 330, 210));

        getContentPane().add(pnl_infor, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 30, 330, 210));

        jLabel5.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/Remove-bg.ai_1715704472375.png"))); // NOI18N
        getContentPane().add(jLabel5, new org.netbeans.lib.awtextra.AbsoluteConstraints(410, 70, -1, -1));
        getContentPane().add(jLabel6, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, -1, -1));

        jLabel7.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/anhnen.jpg"))); // NOI18N
        getContentPane().add(jLabel7, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 1080, 510));

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(ServerFrm.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(ServerFrm.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(ServerFrm.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(ServerFrm.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new ServerFrm().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel lbl_localHost;
    private javax.swing.JLabel lbl_numUsers1;
    private javax.swing.JLabel lbl_port;
    private javax.swing.JPanel pnl_infor;
    private javax.swing.JTable tbl_listUsers;
    // End of variables declaration//GEN-END:variables

    
    private void sendDialogue(String request) {
        String feedBack = "Dialogue ";
        String[] a = request.split(" ");
        String sender = a[1];
        String receiver = a[2];

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement("CALL GETDIALOGUE(?, ?)")) {
            ps.setString(1, sender);
            ps.setString(2, receiver);

            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder feedbackBuilder = new StringBuilder(feedBack);

                while (rs.next()) {
                    feedbackBuilder.append(rs.getString(1)).append(",")
                                    .append(rs.getNString(2)).append(",")
                                    .append(rs.getString(3)).append(",")
                                    .append(rs.getString(4)).append(";");
                }
                feedbackBuilder.append(receiver).append(" ").append(sender);
                this.feedBack = feedbackBuilder.toString();

                for (int i = 0; i < 3; i++) {
                    ClientService.lstSocket.forEach(t -> t.sendMessage(this.feedBack));
                }
            }
        } catch (SQLException ex) {
            Logger.getLogger(ServerFrm.class.getName()).log(Level.SEVERE, null, ex);
        }
    }


    private void updateNewMessage(String request) {
        request = request.substring(request.indexOf(" ") + 1);
        String[] a = request.split(",");
        if (a.length != 3) {
            Logger.getLogger(ServerFrm.class.getName()).log(Level.SEVERE, "Invalid request format: " + request);
            return;
        }

        String content = a[0];
        String sender = a[1];
        String recipient = a[2];

        String sql = "CALL ADDMESSAGE(?, ?, ?)";

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, content);
            ps.setString(2, sender);
            ps.setString(3, recipient);

            ps.executeUpdate();

            sendDialogue("GETDIALOGUE " + recipient + " " + sender);
        } catch (SQLException ex) {
            Logger.getLogger(ServerFrm.class.getName()).log(Level.SEVERE, "SQL error during updateNewMessage", ex);
        }
    }

    private void sendOTP(String email, String codeOTP) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.port", 587);

        Session session = Session.getDefaultInstance(props, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("nguyenhong24062005@gmail.com", "Nguyenhong24@");
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress("nguyenthihong24062005@gmail.com"));
            message.setRecipients(javax.mail.Message.RecipientType.TO, InternetAddress.parse(email));
            message.setSubject("Mã OTP khôi phục mật khẩu");
            message.setText("Mã OTP khôi phục mật khẩu: " + codeOTP);
            // send message
            Transport.send(message);

        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

}
