package net.runelite.mp.audioprobe;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

/** Test-only entry point: no activity, permissions, account import or game-package restart. */
public final class AudioInstrumentation extends Instrumentation {
    private Bundle arguments;

    @Override public void onCreate(Bundle arguments) {
        this.arguments = arguments;
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        boolean success = false;
        try {
            System.setOut(new PrintStream(output, true, "UTF-8"));
            String test = arguments.getString("test", "TestAudioLineContracts");
            String mode = arguments.getString("mode", "");
            System.out.println("PROBE sdk=" + android.os.Build.VERSION.SDK_INT + " test=" + test + " mode=" + mode);
            if (mode.equals("background")) {
                startActivitySync(new android.content.Intent(getTargetContext(), AudioProbeActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                try (FileOutputStream stage = new FileOutputStream(new File(getTargetContext().getFilesDir(), "audio-stage.txt"))) {
                    stage.write("streaming".getBytes("UTF-8"));
                }
            }
            Class.forName(test).getMethod("main", String[].class)
                .invoke(null, (Object) (mode.isEmpty() ? new String[0] : new String[]{mode}));
            if (mode.equals("background")) {
                if (AudioProbeActivity.resumes < 2 || AudioProbeActivity.pauses < 1)
                    throw new AssertionError("Background/resume transition was not exercised");
                System.out.println("PASS background/resume callbacks resumes=" + AudioProbeActivity.resumes + " pauses=" + AudioProbeActivity.pauses);
            }
            success = true;
        } catch (Throwable error) {
            while (error.getCause() != null) error = error.getCause();
            error.printStackTrace(System.out);
        } finally {
            System.setOut(original);
            try (FileOutputStream file = new FileOutputStream(new File(getTargetContext().getFilesDir(), "audio-result.txt"))) {
                output.writeTo(file);
            } catch (Exception error) {
                success = false;
            }
            Bundle result = new Bundle();
            try { result.putString("stream", output.toString("UTF-8")); }
            catch (Exception ignored) { success = false; }
            finish(success ? -1 : 0, result);
        }
    }
}
