package project;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static Map<String, Connection> connectionMap = new ConcurrentHashMap<>();

    public static void sendBroadcastMessage(Message message) {
        for (Connection connection : connectionMap.values()) {
            try {
                connection.send(message);
            } catch (IOException e) {
                ConsoleHelper.writeMessage("Не удалось отправить сообщение");
            }
        }
    }

    private static class Handler extends Thread {
        Socket socket;

        public Handler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            String username = null;
            ConsoleHelper.writeMessage(String.format("Установлено новое соединение с адресом - %s", socket.getRemoteSocketAddress()));
            try (Connection connection = new Connection(socket)) {
                username = serverHandshake(connection);
                sendBroadcastMessage(new Message(MessageType.USER_ADDED, username));
                notifyUsers(connection, username);
                serverMainLoop(connection, username);
            } catch (IOException | ClassNotFoundException e) {
                ConsoleHelper.writeMessage(String.format("Произошла ошибка при обмене данными с удаленым пользователем %s", socket.getRemoteSocketAddress()));
            } finally {
                ConsoleHelper.writeMessage(String.format("Соединение с пользователем на порту %s - закрыто", socket.getRemoteSocketAddress()));
            }

            if (username != null) {
                connectionMap.remove(username);
                sendBroadcastMessage(new Message(MessageType.USER_REMOVED, username));
                ConsoleHelper.writeMessage(String.format("Пользователь %s удален", username));
            }
        }

        private String serverHandshake(Connection connection) throws IOException, ClassNotFoundException {
            Message messageFromClient;

            while (true) {
                Message sendYourNameMessage = new Message(MessageType.NAME_REQUEST, "Назовите ваше имя");
                connection.send(sendYourNameMessage);
                messageFromClient = connection.receive();

                if (messageFromClient.getType() != MessageType.USER_NAME || messageFromClient.getData().trim().isEmpty()) {
                    continue;
                }

                if (connectionMap.get(messageFromClient.getData()) != null) {
                    continue;
                }

                connectionMap.put(messageFromClient.getData(), connection);
                connection.send(new Message(MessageType.NAME_ACCEPTED, String.format("Ваше имя %s - зарегестрированно", messageFromClient.getData())));
                return messageFromClient.getData();
            }
        }

        private void notifyUsers(Connection connection, String username) {
            for (String name : connectionMap.keySet()) {
                if (name.equals(username)) {
                    break;
                }
                try {
                    connection.send(new Message(MessageType.USER_ADDED, name));
                } catch (IOException e) {
                    ConsoleHelper.writeMessage("Не удалось оповестить юзеров");
                }
            }

        }

        private void serverMainLoop(Connection connection, String username) throws IOException, ClassNotFoundException {
            while (true) {
                Message messageFromClient = connection.receive();
                if (messageFromClient.getType() == MessageType.TEXT) {
                    sendBroadcastMessage(new Message(MessageType.TEXT, username + ": " + messageFromClient.getData()));
                } else {
                    ConsoleHelper.writeMessage("Ошибка! Принятое сообщение не является текстом!");
                }
            }
        }
    }

    public static void main(String[] args) {
        int port = ConsoleHelper.readInt();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Сервер запущен");
            while (true) {
                Socket accept = serverSocket.accept();
                Handler handler = new Handler(accept);
                handler.start();
            }
        } catch (IOException e) {
            ConsoleHelper.writeMessage("Произошла ошибка в работе сервера");
        }

    }
}
