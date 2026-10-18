package dev.creategalore.bridge;

/**
 * Coil turns carried through a TFMG Large Transformer. Two Large Coils become the transformer's two halves, but TFMG
 * keeps only their ratio and its loot table drops nothing, so breaking a finished transformer destroyed both wound
 * coils and the steel block. Each half now remembers the turns of the coil it replaced and drops that coil back.
 */
public final class TransformerCoils {
    private TransformerCoils() {}

    /** On LargeCoilBlockEntity: the coil's wound turns. */
    public interface Coil {
        int create_galore_bridge$turns();
    }

    /** On LargeTransformerBlockEntity: the turns of the coil this half replaced, or -1 for transformers built before this fix. */
    public interface Half {
        int create_galore_bridge$coilTurns();

        void create_galore_bridge$setCoilTurns(int turns);
    }
}
