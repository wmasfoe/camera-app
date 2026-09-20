import CoreLocation
import Combine

/// GPS location service — manages CLLocationManager lifecycle and publishes location updates
class LocationService: NSObject, ObservableObject {
    @Published var status: LocationStatus = .disabled
    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined

    private let manager = CLLocationManager()
    private var locationContinuation: ((CLLocation?) -> Void)?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 10
    }

    // MARK: - Public API

    /// Request location authorization
    func requestAuthorization() {
        manager.requestWhenInUseAuthorization()
    }

    /// Enable location tracking
    func enable() {
        switch authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            status = .locating
            manager.requestLocation()
            manager.startUpdatingLocation()
        case .notDetermined:
            requestAuthorization()
        default:
            break
        }
    }

    /// Disable location tracking
    func disable() {
        manager.stopUpdatingLocation()
        status = .disabled
    }

    /// Toggle location on/off
    func toggle() {
        switch status {
        case .disabled:
            enable()
        default:
            disable()
        }
    }

    /// Get current location for photo metadata (async)
    func currentLocation() async -> CLLocation? {
        switch status {
        case .acquired(let loc):
            return loc
        case .locating:
            // Try to get a fresh location
            return await withCheckedContinuation { continuation in
                self.locationContinuation = { loc in
                    continuation.resume(returning: loc)
                }
                manager.requestLocation()
            }
        default:
            return nil
        }
    }

    var isEnabled: Bool {
        if case .disabled = status { return false }
        return true
    }

    var isLocating: Bool {
        if case .locating = status { return true }
        return false
    }

    var acquiredLocation: CLLocation? {
        if case .acquired(let loc) = status { return loc }
        return nil
    }
}

// MARK: - CLLocationManagerDelegate

extension LocationService: CLLocationManagerDelegate {
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        status = .acquired(location)
        locationContinuation?(location)
        locationContinuation = nil
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("Location error: \(error.localizedDescription)")
        locationContinuation?(nil)
        locationContinuation = nil
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            if case .locating = status {} else if case .disabled = status {
                // Don't auto-enable; wait for user toggle
            } else {
                status = .locating
                manager.startUpdatingLocation()
            }
        case .denied, .restricted:
            status = .disabled
        default:
            break
        }
    }
}