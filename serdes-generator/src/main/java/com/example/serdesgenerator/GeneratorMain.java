package com.example.serdesgenerator;

import com.example.binaryserdes.config.ProtocolConfig;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI entry point: {@code --manifest <path> --output <path>}. Loads a {@link GeneratorManifest},
 * resolves each entry's {@code className} via {@link Class#forName(String)} off whatever
 * classpath this jar was launched with (no compile-time dependency on the target schema classes -
 * same "wire by fully-qualified class name" idiom {@code MessageSchemaWiringConfig} uses for
 * {@code messageClass:} in {@code traffic-tool.yml}), generates the {@code *.protocol.json}, and
 * runs a self-check before reporting success.
 */
public final class GeneratorMain {

    private GeneratorMain() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (UsageException e) {
            System.err.println(e.getMessage());
            System.err.println();
            System.err.println("Usage: GeneratorMain --manifest <manifest.yml> --output <output.protocol.json>");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Generation failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        Path manifestPath = null;
        Path outputPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--manifest" -> manifestPath = Path.of(requireValue(args, ++i, "--manifest"));
                case "--output" -> outputPath = Path.of(requireValue(args, ++i, "--output"));
                default -> throw new UsageException("Unrecognized argument: " + args[i]);
            }
        }

        if (manifestPath == null) {
            throw new UsageException("--manifest is required");
        }
        if (outputPath == null) {
            throw new UsageException("--output is required");
        }

        GeneratorManifest manifest = new GeneratorManifestLoader().load(manifestPath);
        List<ProtocolJsonGenerator.RootMessage> roots = resolveRoots(manifest);

        ProtocolConfig config = new ProtocolJsonGenerator().generate(roots);

        ObjectMapper mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        mapper.writeValue(outputPath.toFile(), config);

        List<SelfCheckValidator.Mismatch> mismatches = new SelfCheckValidator().validate(config, roots);
        if (mismatches.isEmpty()) {
            System.out.println("Generated " + outputPath + " (" + roots.size() + " message(s), self-check passed)");
        } else {
            System.out.println("Generated " + outputPath + " (" + roots.size() + " message(s)) - "
                    + "WARNING: self-check found size mismatches:");
            for (SelfCheckValidator.Mismatch mismatch : mismatches) {
                System.out.println("  " + mismatch.messageName() + ": expected " + mismatch.expectedSize()
                        + " bytes (StructSizeCalculator), generated config sums to " + mismatch.actualSize() + " bytes");
            }
        }
    }

    private static List<ProtocolJsonGenerator.RootMessage> resolveRoots(GeneratorManifest manifest) throws ClassNotFoundException {
        List<ProtocolJsonGenerator.RootMessage> roots = new ArrayList<>();
        for (GeneratorManifestEntry entry : manifest.getMessages()) {
            Class<?> messageClass = Class.forName(entry.getClassName());
            roots.add(new ProtocolJsonGenerator.RootMessage(messageClass, entry.getOpcode()));
        }
        return roots;
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new UsageException(flag + " requires a value");
        }
        return args[index];
    }

    private static final class UsageException extends RuntimeException {
        UsageException(String message) {
            super(message);
        }
    }
}
