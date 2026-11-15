package com.takumistudios.takumi_gate.client;

import com.takumistudios.takumi_gate.TakumiGate;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Opens every mod jar the game could still need, so that Takumi Launcher can take read access to {@code mods/} and
 * {@code .fabric/processedMods/} away while the game runs (players then cannot copy the event's mods).
 *
 * <p>Fabric reads classes with {@code URL.openStream()}, which opens each jar once and keeps it in the JDK's jar
 * cache, and finds them through the class loader, which opens jars lazily. Both are forced here: once every jar is
 * open, nothing needs to open them again, and open files keep working after the launcher denies access.
 */
final class JarWarmup {
    static final List<String> DIRS = List.of("mods", ".fabric/processedMods");

    private JarWarmup() {
    }

    /** Returns how many jars were opened. */
    static int run(Path gameDir) {
        ClassLoader loader = JarWarmup.class.getClassLoader();
        try {
            // A lookup that matches nothing makes the class loader open every jar on its class path.
            Enumeration<URL> none = loader.getResources("takumi_gate/warmup/" + System.nanoTime());
            while (none.hasMoreElements()) {
                none.nextElement();
            }
        } catch (IOException e) {
            TakumiGate.LOGGER.debug("No se pudieron recorrer los recursos", e);
        }
        int opened = 0;
        for (String dir : DIRS) {
            Path folder = gameDir.resolve(dir);
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (DirectoryStream<Path> jars = Files.newDirectoryStream(folder, "*.jar")) {
                for (Path jar : jars) {
                    if (cache(jar)) {
                        opened++;
                    }
                }
            } catch (IOException e) {
                TakumiGate.LOGGER.warn("No se pudo recorrer {}", folder, e);
            }
        }
        return opened;
    }

    /** Puts the jar in the JDK's jar cache under every spelling of its path Fabric may use. */
    private static boolean cache(Path jar) {
        Set<Path> forms = new LinkedHashSet<>();
        forms.add(jar.toAbsolutePath().normalize());
        try {
            forms.add(jar.toRealPath());
        } catch (IOException e) {
            TakumiGate.LOGGER.debug("Sin ruta real para {}", jar, e);
        }
        boolean ok = false;
        for (Path form : forms) {
            try {
                URL url = URI.create("jar:" + form.toUri() + "!/").toURL();
                URLConnection connection = url.openConnection();
                connection.setUseCaches(true);
                ((JarURLConnection) connection).getJarFile();
                ok = true;
            } catch (IOException | RuntimeException e) {
                TakumiGate.LOGGER.debug("No se pudo abrir {}", form, e);
            }
        }
        return ok;
    }
}
