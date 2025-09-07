import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class ThreatMapWidget extends JPanel {

    private static final Color BG = Color.BLACK;
    private static final Color OCEAN = new Color(18, 18, 18);

    private final List<Attack> attacks = Collections.synchronizedList(new ArrayList<>());
    private final Timer timer;
    private final Image worldImage;

    // === Terminal log system ===
    private final java.util.List<String> logFeed = new LinkedList<>();
    private final Random rng = new Random();
    private final int MAX_LOGS = 12; // show last 12 lines

    private final String[] sites = {
        "Site-19", "Site-17", "Site-06-3", "Area-14", "Site-64", "Site-11"
    };

    private final String[] statuses = {
        "Suspected", "Confirmed", "Containment", "Recontainment", "Termination"
    };

    private final String[] mtfUnits = {
        "MTF ALPHA-1 'Red Right Hand'",
        "MTF BETA-7 'Maz Hatters'",
        "MTF GAMMA-5 'Red Herrings'",
        "MTF ETA-10 'See No Evil'",
        "MTF NU-7 'Hammer Down'",
        "MTF TAU-5 'Samsara'",
        "MTF RHO-9 'Tech Support'",
        "MTF EPSILON-11 'Nine-Tailed Fox'"
    };

    private int mtfIndex = 0;

    public ThreatMapWidget() {
        setPreferredSize(new Dimension(1200, 600));
        setBackground(BG);

        worldImage = new ImageIcon("world.png").getImage();

        // update animation
        timer = new Timer(30, e -> {
            synchronized (attacks) {
                Iterator<Attack> it = attacks.iterator();
                while (it.hasNext()) {
                    Attack a = it.next();
                    a.progress += 0.01f + a.speed;
                    if (a.progress >= 1.0f) {
                        it.remove();
                    }
                }
            }
            repaint();
        });
        timer.start();

        // auto-generate random attack + log every 5s
        new Timer(5000, e -> {
            addAttack(
                randInRange(-60, 70), randInRange(-180, 180),
                randInRange(-60, 70), randInRange(-180, 180)
            );
        }).start();

        // mouse tooltip lat/lon
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                double[] ll = xyToLatLon(e.getX(), e.getY());
                setToolTipText(String.format("Lat: %.2f, Lon: %.2f", ll[0], ll[1]));
            }
        });
    }

    /** Add an attack event (always creates a log too) */
    public void addAttack(double fromLat, double fromLon, double toLat, double toLon) {
        Attack a = new Attack(fromLat, fromLon, toLat, toLon);
        synchronized (attacks) {
            attacks.add(a);
        }

        // assign next MTF unit
        String mtf = mtfUnits[mtfIndex];
        mtfIndex = (mtfIndex + 1) % mtfUnits.length;

        // push log entry (always paired with line)
        String site = sites[rng.nextInt(sites.length)];
        String status = statuses[rng.nextInt(statuses.length)];
        String entryType = rng.nextBoolean() ? "[THREAT]" : "[INVEST]";
        String newLog = String.format("%s %s | %s | %s", entryType, site, status, mtf);

        logFeed.add(newLog);
        if (logFeed.size() > MAX_LOGS) logFeed.remove(0);

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            // background
            g2.setColor(OCEAN);
            g2.fillRect(0, 0, w, h);

            g2.drawImage(worldImage, 0, 0, w, h, this);

            // grid
            g2.setColor(new Color(80, 80, 80, 40));
            for (int lon = -180; lon <= 180; lon += 30) {
                int x = lonToX(lon, w);
                g2.drawLine(x, 0, x, h);
            }
            for (int lat = -90; lat <= 90; lat += 30) {
                int y = latToY(lat, h);
                g2.drawLine(0, y, w, y);
            }

            // draw attacks
            synchronized (attacks) {
                for (Attack a : attacks) {
                    drawAttack(g2, a, w, h);
                }
            }

            // === TOP RIGHT: SCP Threat Map Details ===
            g2.setFont(new Font("Monospaced", Font.BOLD, 14));
            g2.setColor(new Color(200, 200, 200));
            String[] details = {
                "=== SCP Threat Map ===",
                "Monitoring Global Anomalies",
                "Status: LIVE",
                "Feed: SCiPNET//OVERWATCH"
            };
            int dy = 20;
            for (int i = 0; i < details.length; i++) {
                int strW = g2.getFontMetrics().stringWidth(details[i]);
                g2.drawString(details[i], w - strW - 20, 20 + dy * i);
            }

            // === TERMINAL FEED (bottom-left) ===
            g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
            int y = h - 20;
            for (int i = logFeed.size() - 1; i >= 0; i--) {
                String line = logFeed.get(i);
                if (line != null && !line.isBlank()) {
                    g2.setColor(line.contains("[THREAT]") ? new Color(255, 150, 150) : new Color(180, 255, 180));
                    g2.drawString(line, 20, y);
                    y -= 18;
                }
            }

        } finally {
            g2.dispose();
        }
    }

    private void drawAttack(Graphics2D g2, Attack a, int w, int h) {
        Point2D p1 = latLonToXY(a.fromLat, a.fromLon, w, h);
        Point2D p2 = latLonToXY(a.toLat, a.toLon, w, h);

        double dx = p2.getX() - p1.getX();
        double dy = p2.getY() - p1.getY();
        double dist = Math.hypot(dx, dy);
        double mx = (p1.getX() + p2.getX()) / 2.0;
        double my = (p1.getY() + p2.getY()) / 2.0;
        double nx = -dy / dist;
        double ny = dx / dist;
        double lift = Math.min(160, dist * 0.5) * (0.4 + a.curveBias);
        double cx = mx + nx * lift;
        double cy = my + ny * lift;

        CubicCurve2D curve = new CubicCurve2D.Double();
        curve.setCurve(p1.getX(), p1.getY(), cx, cy, cx, cy, p2.getX(), p2.getY());

        g2.setStroke(new BasicStroke(4f));
        g2.setColor(new Color(150, 150, 150, 70));
        g2.draw(curve);
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(180, 180, 180, 200));
        g2.draw(curve);

        float t = Math.max(0f, Math.min(1f, a.progress));
        Point2D pos = pointOnCubic(p1, new Point2D.Double(cx, cy), p2, t);
        g2.setColor(new Color(200, 200, 200, 240));
        g2.fill(new Ellipse2D.Double(pos.getX() - 3, pos.getY() - 3, 6, 6));

        g2.setColor(new Color(100, 100, 100, 200));
        g2.fill(new Ellipse2D.Double(p1.getX() - 4, p1.getY() - 4, 8, 8));
        g2.fill(new Ellipse2D.Double(p2.getX() - 4, p2.getY() - 4, 8, 8));
    }

    private Point2D pointOnCubic(Point2D p0, Point2D c, Point2D p1, double t) {
        double mt = 1 - t;
        double x = mt * mt * mt * p0.getX() +
                   3 * mt * mt * t * c.getX() +
                   3 * mt * t * t * c.getX() +
                   t * t * t * p1.getX();
        double y = mt * mt * mt * p0.getY() +
                   3 * mt * mt * t * c.getY() +
                   3 * mt * t * t * c.getY() +
                   t * t * t * p1.getY();
        return new Point2D.Double(x, y);
    }

    private int lonToX(double lon, int w) {
        return (int) Math.round((lon + 180.0) / 360.0 * w);
    }

    private int latToY(double lat, int h) {
        return (int) Math.round((90.0 - lat) / 180.0 * h);
    }

    private Point2D latLonToXY(double lat, double lon, int w, int h) {
        double x = (lon + 180.0) / 360.0 * w;
        double y = (90.0 - lat) / 180.0 * h;
        return new Point2D.Double(x, y);
    }

    private double[] xyToLatLon(int x, int y) {
        double lon = (double) x / getWidth() * 360.0 - 180.0;
        double lat = 90.0 - (double) y / getHeight() * 180.0;
        return new double[]{lat, lon};
    }

    private static class Attack {
        final double fromLat, fromLon, toLat, toLon;
        float progress = 0f;
        final float speed;
        final float curveBias;

        Attack(double fLat, double fLon, double tLat, double tLon) {
            this.fromLat = fLat;
            this.fromLon = fLon;
            this.toLat = tLat;
            this.toLon = tLon;
            this.speed = (float) (Math.random() * 0.02);
            this.curveBias = (float) (Math.random() * 0.8);
        }
    }

    // Demo
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("SCP Breach Monitor");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            ThreatMapWidget widget = new ThreatMapWidget();
            frame.add(widget, BorderLayout.CENTER);

            JButton simulate = new JButton("Simulate Attack");
            simulate.addActionListener(e -> widget.addAttack(
                randInRange(-60, 70), randInRange(-180, 180),
                randInRange(-60, 70), randInRange(-180, 180)
            ));

            frame.add(simulate, BorderLayout.SOUTH);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            // demo attacks
            widget.addAttack(37.77, -122.42, 55.76, 37.62);
            widget.addAttack(28.61, 77.20, 40.71, -74.00);
            widget.addAttack(52.52, 13.40, -33.86, 151.21);

            // auto-trigger the simulate button every 6 seconds
            new Timer(6000, e -> simulate.doClick()).start();
        });
    }

    private static double randInRange(double a, double b) {
        return a + Math.random() * (b - a);
    }
}
