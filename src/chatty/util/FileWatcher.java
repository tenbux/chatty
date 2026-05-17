
package chatty.util;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

/**
 * Watch for modifications to a single file.
 * 
 * @author tduva
 */
public class FileWatcher implements Runnable {
    
    private static final Logger LOGGER = Logger.getLogger(FileWatcher.class.getName());
    
    private final WatchService watcher;
    private final Path file;
    private final Path directory;
    private final FileChangedListener listener;
    
    /**
     * Create a watcher that informs the given listener that the given file was
     * modified. The watcher is run in it's own thread. The watcher accumulates
     * events so it takes a few seconds after a modification for the listener to
     * be informed.
     *
     * @param file     The file to watch
     * @param listener The FileChangedListener to be informed
     */
    public static void createFileWatcher(Path file, FileChangedListener listener) {
        if (listener == null || file == null) {
            return;
        }
        try {
            FileWatcher watcher = new FileWatcher(file, listener);
            Thread thread = new Thread(watcher, "FileWatcher");
            thread.setDaemon(true);
            thread.start();
            LOGGER.info("Added file watcher for: "+file.toAbsolutePath());
        } catch (IOException ex) {
            LOGGER.warning("Error adding file watcher for: "+file.toAbsolutePath()+" ["+ex+"]");
        }
    }
    
    private FileWatcher(Path file, FileChangedListener listener) throws IOException {
        directory = file.getParent();
        this.listener = listener;
        this.file = file;
        watcher = FileSystems.getDefault().newWatchService();
        directory.register(watcher, ENTRY_MODIFY);
    }

    @Override
    public void run() {
        for (;;) {
            WatchKey key;
            
            /**
             * Wait for a modification to occur.
             */
            try {
                key = watcher.take();
            } catch (InterruptedException ex) {
                return;
            }
            
            /**
             * Wait a bit for events to accumulate. This prevents several
             * consecutive modifications (e.g. several writes or change of file
             * modification date) from all triggering the listener.
             */
            try {
                TimeUnit.SECONDS.sleep(4);
            } catch (InterruptedException ex) {
                return;
            }
            
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                
                if (kind == OVERFLOW) {
                    continue;
                }

                Path fileName = asPathEvent(event).context();
                Path changedFile = directory.resolve(fileName);
                if (changedFile.equals(file)) {
                    listener.fileChanged();
                }
            }
            
            boolean valid = key.reset();
            if (!valid) {
                break;
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private static WatchEvent<Path> asPathEvent(WatchEvent<?> event) {
        return (WatchEvent<Path>) event;
    }

    public interface FileChangedListener {
        
        /**
         * Called by the watcher when one or more modifiactions are detected.
         */
        void fileChanged();
    }
    
    /**
     * Test/Example.
     * 
     * @param args 
     */
    public static void main(String[] args) {
        final Path file = Paths.get("test/test.txt");
        createFileWatcher(file, () -> System.out.println(file+" changed"));
    }
    
}
