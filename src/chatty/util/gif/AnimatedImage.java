
package chatty.util.gif;

import java.awt.Dimension;

/**
 * An image consisting of several frames (e.g. an animated GIF), intended to be
 * animated by AnimatedImageSource.
 * 
 * @author tduva
 */
public interface AnimatedImage {

    void getFrame(int frame, int[] pixels) throws Exception;
    int getFrameCount();
    int getDelay(int frame);
    Dimension getSize();
    String getName();
    int getPreferredPauseFrame();
    
    static void setAnimationPause(int state) {
        AnimatedImageSource.ANIMATION_PAUSE = state;
    }
    
}
