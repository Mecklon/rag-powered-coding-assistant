package com.mecklon.RagPoweredCodingAssistant;

import java.util.List;

import com.mecklon.RagPoweredCodingAssistant.chunker.AstCodeChunker;
import com.mecklon.RagPoweredCodingAssistant.chunker.CodeChunk;
import com.mecklon.RagPoweredCodingAssistant.chunker.SourceFile;
import com.mecklon.RagPoweredCodingAssistant.chunker.TokenEstimator;
import com.mecklon.RagPoweredCodingAssistant.chunker.TreeSitterCodeParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChunkerSmokeTest {

    @Test
    void chunkJavaClass() {
        String java = """
                package com.example;

                import java.util.List;

                public class UserService {
                    private String name;

                    public UserService(String name) {
                        this.name = name;
                    }

                    public String getName() {
                        return name;
                    }

                    public void setName(String name) {
                        this.name = name;
                    }
                }
                """;
        AstCodeChunker chunker = new AstCodeChunker(new TreeSitterCodeParser(), new TokenEstimator());
        List<CodeChunk> chunks = chunker.chunkFile(new SourceFile("UserService.java", "java", java, "repo1", "main"));
        System.out.println("CHUNKS: " + chunks.size());
        for (CodeChunk c : chunks) {
            System.out.println("--- " + c.getMetadata().getSymbolType() + " " + c.getMetadata().getSymbolName()
                    + " lines " + c.getMetadata().getStartLine() + "-" + c.getMetadata().getEndLine());
        }
        assertTrue(chunks.size() >= 3, "Expected class + methods to be chunked");
    }
}