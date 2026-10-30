import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Generate LegacyBufferedImageGraphics2D.java from the pre-change graphics source by
 * replacing BufferedImageGraphics2D with LegacyBufferedImageGraphics2D. Build with
 * -I tools-src/swing-ui-probe/probe.init.gradle -PgraphicsBaselineSource=/absolute/path/to/that/file
 * and launch the diagnostic Activity with --es probe graphics-benchmark.
 * Results are written to the diagnostic app's files/graphics-benchmark-result.txt.
 */
public final class TestGraphicsFallbackBenchmark {
    private interface Draw { void run(Graphics2D g, BufferedImage source); }
    private static final class Target extends BufferedImage {
        long reads, writes;
        Target(int w, int h) { super(w, h, TYPE_INT_ARGB); }
        @Override public int[] getRGB(int x,int y,int w,int h,int[] data,int off,int stride) {
            reads+=(long)w*h; return super.getRGB(x,y,w,h,data,off,stride);
        }
        @Override public void setRGB(int x,int y,int w,int h,int[] data,int off,int stride) {
            writes+=(long)w*h; super.setRGB(x,y,w,h,data,off,stride);
        }
    }
    public static String run() throws Exception {
        StringBuilder report=new StringBuilder();
        report.append("Controlled old/new fallback comparison; milliseconds per draw, not game FPS.\n");
        benchmark(report,"small-gradient",(g,s)->{
            g.setPaint(new GradientPaint(0,0,Color.RED,1024,768,Color.BLUE));g.fillRect(700,500,20,16);
        });
        benchmark(report,"rotated-solid",(g,s)->{
            g.setColor(Color.GREEN);AffineTransform old=g.getTransform();
            g.rotate(.15,700,500);g.fillRect(700,500,20,16);g.setTransform(old);
        });
        benchmark(report,"cropped-image",(g,s)->g.drawImage(s,740,500,700,532,500,400,510,408,null));
        benchmark(report,"fractional-text",(g,s)->{
            g.setColor(Color.WHITE);g.drawString("Fallback test",700.5f,500.5f);
        });
        return report.append("PASS fallback benchmark pixel comparisons\n").toString();
    }
    private static void benchmark(StringBuilder report,String name,Draw draw) throws Exception {
        Target[] targets={new Target(1024,768),new Target(1024,768)};
        Graphics2D[] graphics={
            (Graphics2D)Class.forName("net.runelite.awt.impl.LegacyBufferedImageGraphics2D")
                .getConstructor(BufferedImage.class).newInstance(targets[0]),
            targets[1].createGraphics()
        };
        BufferedImage source=new BufferedImage(1024,768,BufferedImage.TYPE_INT_ARGB);
        for(int y=399;y<410;y++)for(int x=499;x<512;x++)source.setRGB(x,y,0xff4488cc);
        long[][] samples=new long[2][7];
        try {
            for(int which=0;which<2;which++)for(int i=0;i<2;i++)draw.run(graphics[which],source);
            for(Target target:targets){target.reads=0;target.writes=0;}
            // Alternate order to reduce warmup/thermal/order bias.
            for(int round=0;round<7;round++)for(int step=0;step<2;step++){
                int which=(round+step)%2;
                long start=System.nanoTime();draw.run(graphics[which],source);
                samples[which][round]=System.nanoTime()-start;
            }
            for(long[] times:samples)Arrays.sort(times);
            long oldReads=targets[0].reads,newReads=targets[1].reads;
            int[] before=targets[0].getRGB(0,0,1024,768,null,0,1024);
            int[] after=targets[1].getRGB(0,0,1024,768,null,0,1024);
            int different=0,maxError=0;
            for(int i=0;i<before.length;i++){
                if(before[i]!=after[i])different++;
                for(int shift=0;shift<=24;shift+=8)
                    maxError=Math.max(maxError,Math.abs((before[i]>>>shift&255)-(after[i]>>>shift&255)));
            }
            report.append(String.format(Locale.ROOT,
                "%s oldMedianMs=%.3f newMedianMs=%.3f speedup=%.2fx oldReadPixels=%d newReadPixels=%d differentPixels=%d maxChannelError=%d\n",
                name,samples[0][3]/1e6,samples[1][3]/1e6,(double)samples[0][3]/samples[1][3],
                oldReads,newReads,different,maxError));
            if(maxError>1)throw new AssertionError(report.toString());
        } finally {for(Graphics2D g:graphics)g.dispose();}
    }
}
