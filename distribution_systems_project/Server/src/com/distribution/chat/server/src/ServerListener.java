package com.distribution.chat.server.src;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import static com.distribution.chat.server.src.Server.writeToLog;

/**
 * A class represented a thread that listen for new connections with clients,
 * in order to release the ui main thread to interact with the user
 */
public class ServerListener extends Thread {

    private ServerSocket serverSocket;
    public volatile boolean stop;

    /**
     * Constructor
     * @param serverSocket
     */
    ServerListener(ServerSocket serverSocket) {
        this.stop = false;
        this.serverSocket = serverSocket;
    }

    /**
     * A method that the thread will run when starts
     * This method waits for new connections with clients
     */
    @Override
    public void run() {
        try {
            while (!stop) {
                if (!stop && serverSocket != null && !serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    HandleClientThread hc = new HandleClientThread(clientSocket);
                    hc.start();
                    Server.handleClients.add(hc);
                }
            }
        }
        catch(SocketException e)
        {
            //Server close serverSocket, do nothing
            //Write to log
            writeToLog(Server.LOG_LEVEL.INFO.toString(), "Server stop listening to ip " + serverSocket.getInetAddress() + " on port " + serverSocket.getLocalPort());
        }
        catch (IOException e1) {
            System.out.println("Error while try to accept server clientSocket and create handle client thread");
        } finally {
            try {
                if (serverSocket != null && !serverSocket.isClosed())
                    serverSocket.close();
            } catch (IOException e) {
                System.out.println("Error while try to close the server socket " + serverSocket);
            }
        }
    }

}
