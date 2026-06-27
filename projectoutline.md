# OsmAndRadar – Bike Computer with Radar Integration

## Vision

A comprehensive open-source bike computer built on OsmAnd, combining offline navigation, ANT+/BLE sensor integration, and rear radar support. The goal is **not** to replicate Cadence, but to create the **best bike profiling solution** on top of OsmAnd's proven navigation foundation.

## Why OsmAnd?

OsmAnd already provides:
- ✅ **Strong Offline Navigation**: offline maps, routing, turn-by-turn guidance, re-routing
- ✅ **Sensor Infrastructure**: ANT+ and BLE device management
- ✅ **Supported Sensors**: Power, Cadence, Speed, Heart Rate, Temperature
- ✅ **Recording & Analysis**: track recording with sensor data, GPX export compatible with Strava
- ✅ **Plugin Architecture**: modular, established system for extending functionality
- ✅ **Widget System**: customizable on-screen displays for sensor data
- ✅ **Precedent for Overlays**: AIS ship radar proof-of-concept for relative positioning and alerts

What we're adding:
- 🚨 **Rear Radar Support**: Gardia/Varia detection via BLE
- 🎨 **Bike-Optimized UI**: dashboard layout for cycling workouts
- 📈 **Training Views**: workout analysis and performance metrics
- ☁️ **Cloud Workflow**: streamlined export and upload to training platforms (Strava, etc.)

## Project Scope

### Phase 1: Foundation
- Clone and build OsmAnd successfully
- Understand External Sensors plugin architecture
- Map data flow: Sensor → Widget → Recording

### Phase 2: Radar Integration
- Implement new `RadarBleDevice` device type
- BLE scanning and pairing for Gardia/Varia
- Decode radar-specific data packets
- Radar widget display (distance, alert states)

### Phase 3: Map Overlay & Alerts
- `RadarMapLayer`: visual representation of nearby vehicles
- Alert system: sound, haptic feedback, color coding
- Integration with existing widget framework

### Phase 4: UX & Training
- Bike-specific dashboard layout
- Training mode (FTP display, intervals, zones)
- Performance metrics post-ride
- Strava/Cloud export workflow

## Technical Architecture

```
OsmAndRadar (Fork)
├── core/
│   ├── Navigation (unchanged)
│   ├── Routing (unchanged)
│   └── Maps (unchanged)
├── plugins/
│   ├── externalSensors/ (existing)
│   └── bikeRadar/ (new)
│       ├── RadarBleDevice.java
│       ├── RadarDecoder.java
│       ├── RadarMapLayer.java
│       ├── RadarWidget.java
│       └── RadarAlertManager.java
├── recording/
│   └── Track recording with radar data (existing infrastructure)
└── ui/
    └── Bike-specific dashboard layouts (new)
```

## Git Strategy

- **Upstream**: `https://github.com/osmandapp/OsmAnd.git`
- **Origin (Fork)**: `https://github.com/millorg-dev/OsmAndRadar.git`
- **Active Branch**: `feature/bike-radar`

Regular upstream syncing ensures we stay current with OsmAnd updates while keeping our radar-specific code isolated and modular.

## Licensing

- **OsmAnd Base**: GPLv3
- **UI/Assets**: Check OsmAnd terms for public distribution
- **OsmAndRadar Custom Code**: GPLv3 (compatible with base)
- **Private Use**: No restrictions; redistribution requires careful license review

For this private/experimental phase, we focus on technical implementation. Distribution strategy will be revisited if the project matures.

## Success Criteria

1. ✅ OsmAnd builds and runs locally
2. ✅ Rear radar device detectable via BLE, data parsed
3. ✅ Radar widget displays distance and alerts on map
4. ✅ Track recording includes radar event markers
5. ✅ Clean modular code, easy to maintain and extend
6. ✅ Minimal changes to OsmAnd core (all radar logic isolated in plugin)

## Next Steps

1. Build OsmAnd locally (Phase 1)
2. Locate and document External Sensors architecture
3. Design RadarBleDevice class structure
4. Implement BLE scanning for known radar hardware
5. Begin data packet decoding
