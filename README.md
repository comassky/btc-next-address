# Bitcoin Next Address - BIP84

A Quarkus application to find the next unused Bitcoin BIP84 address from an extended public key (xpub/zpub).

The application provides a simple web interface to display the address and its QR code, as well as a REST API.

## Features

- ✅ **BIP84 (Native SegWit)** Bitcoin address derivation.
- ✅ **Backend QR code generation** for a lightweight client interface.
- ✅ Address usage verification via external blockchain APIs (Blockchair, Blockchain.info, Mempool.space).
- ✅ **Persistent cache** for already checked addresses to minimize API calls, with index tracking for optimized searches.
- ✅ **Optimized algorithm**: Parallel address derivation, batch processing (50 addresses), starts from last known used index +1, always double-checks cached unused addresses on blockchain.
- ✅ Simple REST API and intuitive web interface.
- ✅ Built as a **native executable** using GraalVM for fast startup and low memory footprint.
- ✅ **Docker-ready** for deployment.

## Tech Stack

- **Backend**: Java 21 (LTS), Quarkus
- **QR Code Generation**: ZXing (Zebra Crossing)
- **Bitcoin**: BitcoinJ
- **Build**: Maven, GraalVM

## Configuration

The configuration file is located at `src/main/resources/application.properties`.

- `bitcoin.xpub`: Your extended public key (xpub or zpub). **It is strongly recommended to set this via an environment variable.**
- `bitcoin.network`: The Bitcoin network to use (`mainnet` or `testnet`).
- `bitcoin.cache.path`: The **directory** where the cache file (`address-cache.json`) will be stored.
- `bitcoin.gap-limit`: The maximum number of addresses to check before giving up (default: 100).

Example of setting the xpub via an environment variable:
```bash
export BITCOIN_XPUB="xpub7ogocZ..."
```

## Running the Application

### Development Mode
To run the application in development mode with hot-reload:
```bash
./mvnw quarkus:dev
```
The application will be available at `http://localhost:8080`.

### Native Build & Docker

This application is designed to be run as a native executable inside a Docker container.

**1. Build the Docker Image:**

The provided `Dockerfile` uses a multi-stage approach to build the native executable and then copies it into a minimal runtime image.
```bash
docker build -t quarkus/next-address .
```

**2. Run the Container:**

To ensure cache persistence across restarts, it is crucial to mount a volume to the container's cache directory.

```bash
# Create a local directory for the cache
mkdir -p ./btc-cache

# Run the container with the volume mount
docker run -i --rm \
  -p 8080:8080 \
  -v ./btc-cache:/work/cache \
  -e BITCOIN_XPUB="your_xpub_here" \
  quarkus/next-address
```
- `-v ./btc-cache:/work/cache`: Mounts the local `./btc-cache` directory to the `/work/cache` directory inside the container.
- `-e BITCOIN_XPUB="..."`: Securely sets your xpub.

## REST API

### Main Endpoint

Finds the next unused address.
```
POST /api/address/next
Content-Type: application/json

// The request body can be empty or specify a starting index
{
  "startIndex": 0 
}

// Response
{
  "address": "bc1q...",
  "index": 42,
  "qrCodeImage": "data:image/svg+xml;base64,...",
  "salt": "...",
  "hash": "..."
}
```

### Verify Endpoint

Verifies if a specific address belongs to the configured xpub.
```
POST /api/address/verify
Content-Type: application/json

{
  "address": "bc1q..."
}

// Response
{
  "valid": true,
  "index": 42
}
```

## Performance Optimizations

- **Parallel derivation**: Addresses in a batch are derived concurrently for faster processing.
- **Batch processing**: Checks 50 addresses per batch to reduce API calls.
- **Smart starting point**: Begins searching from the last known used index +1, skipping already used addresses.
- **Blockchain API prioritization**: Uses Blockchair for batch support, falls back to Blockchain.info, then Mempool.space.
- **Persistent cache**: Stores results with timestamps and indices to minimize redundant checks.

## License

MIT
