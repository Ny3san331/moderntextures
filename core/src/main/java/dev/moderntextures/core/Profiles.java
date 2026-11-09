package dev.moderntextures.core;

import java.util.*;

/** Release protocol IDs from ViaVersion 5.12.2. One resource-compatible representative per protocol. */
public final class Profiles {
    public record Profile(String version, int protocol, String registry) {}
    public static final List<Profile> ALL = List.of(
        p("1.7.5",4,"1.7.10"), p("1.7.10",5,"1.7.10"), p("1.8.9",47,"1.8"),
        p("1.9",107,"1.9"), p("1.9.1",108,"1.9"), p("1.9.2",109,"1.9"), p("1.9.4",110,"1.9.4"),
        p("1.10.2",210,"1.10"), p("1.11",315,"1.11"), p("1.11.2",316,"1.11"),
        p("1.12",335,"1.12"), p("1.12.1",338,"1.12"), p("1.12.2",340,"1.12"),
        p("1.13",393,"1.13"), p("1.13.1",401,"1.13"), p("1.13.2",404,"1.13.2"),
        p("1.14",477,"1.14"), p("1.14.1",480,"1.14"), p("1.14.2",485,"1.14"),
        p("1.14.3",490,"1.14"), p("1.14.4",498,"1.14"),
        p("1.15",573,"1.15"), p("1.15.1",575,"1.15"), p("1.15.2",578,"1.15"),
        p("1.16",735,"1.16"), p("1.16.1",736,"1.16"), p("1.16.2",751,"1.16.2"),
        p("1.16.3",753,"1.16.2"), p("1.16.5",754,"1.16.2"),
        p("1.17",755,"1.17"), p("1.17.1",756,"1.17"), p("1.18.1",757,"1.18"), p("1.18.2",758,"1.18"),
        p("1.19",759,"1.19"), p("1.19.2",760,"1.19"), p("1.19.3",761,"1.19.3"), p("1.19.4",762,"1.19.4"),
        p("1.20.1",763,"1.20"), p("1.20.2",764,"1.20.2"), p("1.20.4",765,"1.20.3"), p("1.20.6",766,"1.20.5"),
        p("1.21.1",767,"1.21"), p("1.21.3",768,"1.21.2"), p("1.21.4",769,"1.21.4"),
        p("1.21.5",770,"1.21.5"), p("1.21.6",771,"1.21.6"), p("1.21.8",772,"1.21.7"),
        p("1.21.10",773,"1.21.9"), p("1.21.11",774,"1.21.11"), p("26.1.2",775,"26.1"), p("26.2",776,"26.2")
    );
    private static Profile p(String version, int protocol, String registry) { return new Profile(version, protocol, registry); }
    private Profiles() {}
    public static Optional<Profile> protocol(int id) { return ALL.stream().filter(p -> p.protocol == id).findFirst(); }
    public static Profile version(String version) {
        return ALL.stream().filter(p -> p.version.equals(version)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Perfil desconhecido: " + version + ". Use --list."));
    }
}
