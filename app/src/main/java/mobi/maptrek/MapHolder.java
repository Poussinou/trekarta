/*
 * Copyright 2018 Andrey Novikov
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package mobi.maptrek;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.oscim.core.GeoPoint;
import org.oscim.map.Map;

public interface MapHolder {
    Map getMap();

    void updateMapViewArea();

    void disableLocations();

    void disableTracking();

    void shareLocation(@NonNull GeoPoint coordinates, @Nullable String name);

    void navigateTo(@NonNull GeoPoint coordinates, @Nullable String name);

    boolean isNavigatingTo(@NonNull GeoPoint coordinates);

    void stopNavigation();

    /**
     * Adds location state change listener and then calls listener with current state.
     */
    void addLocationStateChangeListener(LocationStateChangeListener listener);

    void removeLocationStateChangeListener(LocationStateChangeListener listener);

    void addLocationChangeListener(LocationChangeListener listener);

    void removeLocationChangeListener(LocationChangeListener listener);

    void setMapLocation(@NonNull GeoPoint point);

    void showMarker(@NonNull GeoPoint point, @Nullable String name);

    void removeMarker();
}
