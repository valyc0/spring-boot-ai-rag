# Design: ricerca AI ibrida in un'app con ricerca testuale esistente

> Considerazioni di design per aggiungere una ricerca "stile Google" (risposta AI + lista chunk)
> a un'app Vaadin che oggi offre già una ricerca testuale paginata con filtri.

## Contesto attuale

- L'app oggi ha una schermata Vaadin in cui il cliente:
  1. indicizza i documenti;
  2. cerca con **filtri configurabili** (`department`, `langId`, data, ecc.);
  3. ottiene una lista di **chunk ordinati per score e paginati** (es. primi 20, poi pagina 2...).
- Il cliente vorrebbe una pagina "stile Google": in alto una **risposta AI** generata dal contesto,
  sotto la **lista dei chunk** che già conosce (ordinata per score, paginata).
- Le due zone hanno requisiti opposti: la risposta vuole *qualità del contesto*, la lista vuole
  *stabilità dell'ordinamento e paginazione*.

## Decisione consigliata: due ricerche separate, filtri condivisi

**Un'unica ricerca ibrida per tutto è sconsigliata.** Motivo: fusione RRF + paginazione soffrono insieme
(i rank fusi non danno un ordine totale stabile e ripetibile tra le pagine, e `size/from` su una fusione
è fragile). Meglio separare le due zone.

```
pagina (domanda + filtri già selezionati)
│
├─ Ricerca 1 (esistente, invariata) ── BM25 + filtri + topK/paging ──► lista chunk ordinata per score
│
└─ Ricerca 2 (nuova, per la risposta) ── RRF(BM25, kNN) + stessi filtri, top 5-8 ──► ChatClient ──► risposta AI
```

### Ricerca 1 — la lista chunk (in basso)

- È la ricerca attuale: **NON va ibridizzata**.
- Già funziona, è paginata e stabile, ordinata per score. Resta su BM25 con score deterministico.
- Se in futuro si vuole migliorare il ranking della lista, si può ibridizzare **anche** la lista
  (kNN + BM25), ma mantenendo: top-K per la risposta senza paging, e lista paginata stabile a parte.

### Ricerca 2 — la risposta AI (in alto)

- **Ibridata sì**, ma senza paginazione: bastano i **top 5-8 chunk** come contesto per il LLM.
- RRF (Reciprocal Rank Fusion) tra BM25 e kNN: recupera sia la corrispondenza testuale esatta
  sia la somiglianza semantica, massimizzando la qualità del contesto e quindi della risposta.

### Regola d'oro: stessi filtri

Entrambe le ricerche devono applicare **gli stessi filtri** selezionati dal cliente sullo schermo.
Altrimenti l'AI risponde usando chunk che l'utente non vede nell'elenco (o viceversa).

## Perché non "due indici" (indice custom testo + indice Spring AI vettoriale)

Opzione valutata e sconsigliata (salvo prototipo veloce):
- **Dato duplicato e da sincronizzare**: ogni documento andrebbe scritto in due indici, con tutti i
  problemi di aggiornamento/cancellazione su entrambi.
- **Il risultato della query AI "non parla con" l'indice principale**: i chunk recuperati dall'indice
  Spring AI hanno solo `content`/`metadata`/`embedding`; per arricchirli coi campi di dominio
  (`contentId`, `langId`) servirebbe un secondo giro di query sul proprio indice (join manuale).
- Il vantaggio (zero codice custom, chunking+embedding gratis) è reale, ma lo si paga appena i dati
  crescono o cambiano.

## Forma consigliata dell'endpoint

Un unico endpoint che riceve `question` + filtri, esegue la lista paginata (come oggi) **e** calcola la
risposta con la query ibrida separata, restituendo:

```json
{
  "answer": "...risposta AI...",
  "total": 142,
  "chunks": ["...i chunk paginati come oggi..."]
}
```

## Nota sui due tipi di ricerca di base

- **BM25** (testuale): trova chunk con le stesse parole della domanda. Perfetto per termini precisi
  ("rimborso benzina", codici, numeri). Fallisce se la domanda usa parole diverse dal documento.
- **kNN** (vettoriale): trova chunk *simili nel significato*, anche senza parole in comune. Perfetto
  per parafrasi, gergo, lingue diverse. Può però recuperare rumore e pesare meno i termini esatti.

La ricerca ibrida esegue entrambe e fonde i risultati (RRF: punteggio basato sulla *posizione* in ogni
classifica, non sul punteggio grezzo → robusto).