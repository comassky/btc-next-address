# Bitcoin Next Address - BIP84 Explorer

Une application Quarkus pour trouver la prochaine adresse Bitcoin BIP84 non utilisée à partir d'une clé publique étendue (zpub).

## Fonctionnalités

- ✅ Dérivation BIP84 des adresses Bitcoin
- ✅ Vérification d'utilisation des adresses via blockchain
- ✅ Cache JSON pour les adresses déjà testées
- ✅ API REST simple
- ✅ Interface web avec QR codes
- ✅ Support de la recherche optimisée avec sel personnalisé

## Architecture

```
src/
├── main/
│   ├── java/com/btc/address/
│   │   ├── bitcoin/        # Logique BIP84
│   │   ├── blockchain/     # Vérification adresses
│   │   ├── cache/          # Gestion cache JSON
│   │   ├── service/        # Service métier
│   │   ├── resource/       # API REST
│   │   └── AddressApplication.java
│   └── resources/
│       ├── application.properties
│       └── META-INF/resources/
│           └── index.html  # Interface web
└── test/                   # Tests unitaires
```

## Prérequis

- Java 17+
- Maven 3.8.1+

## Compilation

```bash
mvn clean package
```

## Exécution

### Mode développement
```bash
mvn quarkus:dev
```

### Mode production
```bash
java -jar target/quarkus-app/quarkus-run.jar
```

L'application sera accessible à: `http://localhost:8080`

## API REST

### Endpoint Principal
```
POST /api/address/next
Content-Type: application/json

{
  "zpub": "zpub1234...",
  "startIndex": 0,
  "salt": "votre-sel-optionnel"
}

Response:
{
  "address": "bc1q...",
  "publicKey": "...",
  "index": 5,
  "salt": "...",
  "hash": "..."
}
```

### Health Check
```
GET /api/address/health

Response:
{
  "status": "Address service is running"
}
```

## Cache

Les adresses testées sont sauvegardées dans `address-cache.json` à la racine du projet:

```json
{
  "hash1": {
    "hash": "abc123...",
    "salt": "salt123...",
    "used": false,
    "timestamp": 1707477600000
  }
}
```

## Configuration

Éditez `src/main/resources/application.properties`:

```properties
quarkus.application.name=next-address
quarkus.http.port=8080
bitcoin.cache.file=address-cache.json
bitcoin.network=mainnet
```

## Vérification des Adresses

L'application utilise l'API Blockchair pour vérifier si une adresse a des transactions. 
Alternative: Blockchain.com API.

⚠️ **Note**: Les API publiques ont des limites de débit. Utilisez un API key personnel pour la production.

## Utilisation Web

1. Accédez à `http://localhost:8080`
2. Collez votre ZPUB
3. Définissez un index de départ (optionnel)
4. Cliquez sur "Find Address"
5. L'adresse et le QR code s'affichent
6. Scannez le QR code ou copiez l'adresse

## Notes Importantes

- **BIP84 Path**: `m/84'/0'/0'/0/{index}` pour adresses externes (testnet: `m/84'/1'/0'/0/{index}`)
- **Hash**: SHA-256(address + salt) pour identification robuste
- **Cache**: Évite les requêtes répétées à la blockchain
- **Réseau**: Configuré pour mainnet Bitcoin

## Sécurité

- Les clés privées ne sont JAMAIS traitées
- Seules les clés publiques étendues (zpub) sont utilisées
- Le cache stocke uniquement les hash, pas les adresses brutes
- Salt aléatoire pour chaque recherche

## Troubleshooting

### "Invalid zpub"
- Vérifiez que le zpub commence par `zpub` ou `xpub`
- Assurez-vous que c'est une clé pour le mainnet

### Adresses non trouvées
- Augmentez `maxAttempts` dans `AddressService.java`
- Vérifiez la connexion internet et l'API blockchain
- Consultez les logs de la console

### Erreurs de rate limiting
- Attendez quelques minutes
- Considérez l'utilisation d'une API key personnelle

## Développement

```bash
# Dev avec hot reload
mvn quarkus:dev

# Tests
mvn test

# Build optimisé
mvn package -DskipTests
```

## License

MIT
