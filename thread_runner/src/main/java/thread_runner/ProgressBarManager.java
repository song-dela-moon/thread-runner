package thread_runner;

import java.util.concurrent.atomic.AtomicBoolean;

public class ProgressBarManager {

    private final ConsoleTerminal terminal;
    
    private int[] playerProgress;
    private boolean[] isPlayerWaiting; 
    private String[] rankDisplay;      
    private int rankCounter = 1;
    
    private int[] redTeamAtSpot = {1, 2, 3, 4};
    private final Object redTeamLock = new Object();
    
    private int[] blueTeamAtSpot = {1, 2, 3, 4};
    private final Object blueTeamLock = new Object();
    
    private volatile Boolean isBlueTeamWin = null;
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private Thread renderThread;

    public ProgressBarManager(ConsoleTerminal terminal) {
        this.terminal = terminal;
        this.playerProgress = new int[getPlayerCount()];
        this.isPlayerWaiting = new boolean[getPlayerCount()];
        this.rankDisplay = new String[getPlayerCount()];

        // 초기화 시 화면 정리
        terminal.print("\033[2J\033[H\033[?25l");

        startRenderThread();
    }

    private void startRenderThread() {
        renderThread = new Thread(() -> {
            while (isRunning.get()) {
                try {
                    render();
                    Thread.sleep(33); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        renderThread.setDaemon(true); 
        renderThread.start();
    }

    private void render() {
        StringBuilder sb = new StringBuilder();

        // 1. 커서 홈으로 이동
        sb.append("\u001B[H"); 

        // [핵심 변경] ConsoleTerminal에 있는 로고를 가져와서 버퍼에 담음
        sb.append(terminal.getLogo());

        // 2. 플레이어 바 출력
        synchronized (this) { 
            for (int i = 0; i < getPlayerCount(); i++) {
                String color;
                if (isPlayerWaiting[i]) {
                    color = ColorCode.lime;
                } else {
                    color = isBlueTeam(i) ? ColorCode.blue : ColorCode.red;
                }

                String name = Main.playerNames.get(i);
                int percent = playerProgress[i];
                String bar = formatBar(name, percent, color);
                String rank = (rankDisplay[i] != null) ? rankDisplay[i] : "";

                sb.append(bar).append(rank).append("\u001B[K\n");

                if (i == (getPlayerCount() / 2) - 1) {
                    sb.append("\u001B[K\n"); 
                }
            }
        }

        // 3. 화면 하단 잔상 제거
        sb.append("\u001B[J");

        // 4. 일괄 출력
        terminal.print(sb.toString());
        terminal.flush();
    }

    private String formatBar(String name, int percent, String color) {
        int width = 50; 
        int filled = (int) (width * (percent / 100.0));
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(" %-8s: [", name)); 
        
        sb.append(color);
        for (int i = 0; i < width; i++) {
            if (i < filled) sb.append("■");
            else sb.append(" ");
        }
        sb.append(ColorCode.reset).append("] ").append(String.format("%3d%%", percent));
        return sb.toString();
    }

    // 로직 업데이트 메서드들 (이전과 동일)
    public synchronized void update(int playerIndex, int percent) {
        update(playerIndex, percent, false);
    }

    public synchronized void update(int playerIndex, int percent, boolean isWaiting) {
        if (playerProgress[playerIndex] != 0 && percent == 100) {
            if (isBlueTeamWin == null) isBlueTeamWin = isBlueTeam(playerIndex);
        }
        playerProgress[playerIndex] = percent;
        isPlayerWaiting[playerIndex] = isWaiting;
    }

    public synchronized void completeTask(String threadName, int index) {
        int currentRank = rankCounter++;
        String trophyColor = isBlueTeam(index) ? ColorCode.blue : ColorCode.red;
        rankDisplay[index] = String.format("  %s%d등%s", trophyColor, currentRank, ColorCode.reset);
    }

    private boolean isBlueTeam(int playerIndex) {
        return playerIndex < getPlayerCount() / 2;
    }

    private int getPlayerCount() {
        return Main.playerCount;
    }

    public void waitForTeam(int threadIndex, int spot) throws InterruptedException {
        boolean isBlue = isBlueTeam(threadIndex);
        Object lock = isBlue ? blueTeamLock : redTeamLock;
        int[] waitCount = isBlue ? blueTeamAtSpot : redTeamAtSpot;

        synchronized (lock) {
            waitCount[spot]--; 

            if (waitCount[spot] > 0) {
                update(threadIndex, playerProgress[threadIndex], true);
                lock.wait();
            } else if (waitCount[spot] == 0) {
                update(threadIndex, playerProgress[threadIndex], true);
                Thread.sleep(2000);
                lock.notifyAll();
            }
        }
    }

    public synchronized boolean cleanUpAndgetGameResult() {
        isRunning.set(false);
        try {
            if (renderThread != null) renderThread.join(100);
        } catch (InterruptedException e) { e.printStackTrace(); }

        render();

        terminal.print("\033[?25h"); 
        return isBlueTeamWin != null ? isBlueTeamWin : false;
    }
}
