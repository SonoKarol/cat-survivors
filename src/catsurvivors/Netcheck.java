package catsurvivors;

/**
 * Diagnostica di rete per il co-op via internet: prova ad aprire la porta sul router
 * con UPnP e riporta IP locale, IP pubblico e l'indirizzo da dare agli amici.
 * La mappatura di prova viene rimossa al termine. Uso: java -cp out catsurvivors.Main --netcheck
 */
final class Netcheck {
    private Netcheck() {}

    static void run() {
        int port = Net.DEFAULT_PORT;
        String lan = Server.lanIp();
        System.out.println("=== Cat Survivors — diagnostica co-op ===");
        System.out.println("IP locale (LAN):  " + lan + ":" + port);
        System.out.println("Sondaggio del router via UPnP (qualche secondo)...");

        Upnp.Result r = Upnp.openPort(port, lan);
        System.out.println();
        System.out.println(r.message);
        if (r.externalIp != null) {
            System.out.println("IP pubblico:      " + r.externalIp + ":" + port);
        }
        System.out.println();

        if (r.mapped && r.externalIp != null) {
            System.out.println("RISULTATO: OK. I tuoi amici (fuori dalla LAN) useranno questo indirizzo:");
            System.out.println("    " + r.externalIp + ":" + port);
            System.out.println("Quando ospiti dal gioco, la porta viene aperta automaticamente allo stesso modo.");
            Upnp.closePort(r);
            System.out.println("(mappatura di prova rimossa)");
        } else if (r.externalIp != null) {
            System.out.println("RISULTATO: UPnP non disponibile. Apri manualmente sul router il");
            System.out.println("port forwarding della porta TCP " + port + " verso " + lan + ",");
            System.out.println("poi gli amici useranno " + r.externalIp + ":" + port + ".");
            System.out.println("In alternativa, una VPN tipo Tailscale evita del tutto il port forwarding.");
        } else {
            System.out.println("RISULTATO: impossibile contattare il router via UPnP.");
            System.out.println("Opzioni: 1) attiva UPnP nel router e riprova;");
            System.out.println("         2) port forwarding manuale della porta TCP " + port + " verso " + lan + ";");
            System.out.println("         3) usa una VPN tipo Tailscale/Radmin (nessun forwarding).");
        }
    }
}
