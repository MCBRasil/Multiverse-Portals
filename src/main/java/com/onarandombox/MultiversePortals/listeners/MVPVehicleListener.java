/*
 * Multiverse 2 Copyright (c) the Multiverse Team 2011.
 * Multiverse 2 is licensed under the BSD License.
 * For more information please check the README.md file included
 * with this project
 */

package com.onarandombox.MultiversePortals.listeners;

import com.onarandombox.MultiverseCore.api.MVDestination;
import com.onarandombox.MultiverseCore.destination.InvalidDestination;
import com.onarandombox.MultiverseCore.utils.LocationManipulation;
import com.onarandombox.MultiverseCore.utils.SafeTTeleporter;
import com.onarandombox.MultiversePortals.MVPortal;
import com.onarandombox.MultiversePortals.MultiversePortals;
import com.onarandombox.MultiversePortals.PortalPlayerSession;
import com.onarandombox.MultiversePortals.destination.PortalDestination;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Event.Type;
import org.bukkit.event.vehicle.VehicleListener;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;

import java.util.Date;

public class MVPVehicleListener extends VehicleListener {
    private MultiversePortals plugin;

    public MVPVehicleListener(MultiversePortals plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onVehicleMove(VehicleMoveEvent event) {
        if (event.getVehicle().getPassenger() instanceof Player) {
            Vehicle v = event.getVehicle();
            Player p = (Player) v.getPassenger();
            PortalPlayerSession ps = this.plugin.getPortalSession(p);
            ps.setStaleLocation(v.getLocation(), Type.VEHICLE_MOVE);

            if (ps.isStaleLocation()) {
                return;
            }

            // Teleport the Player
            teleportVehicle(p, v, event.getTo());
        }
    }

    private boolean teleportVehicle(Player p, Vehicle v, Location to) {
        PortalPlayerSession ps = this.plugin.getPortalSession(p);
        MVPortal portal = ps.getStandingInPortal();
        // If the portal is not null
        // AND if we did not show debug info, do the stuff
        // The debug is meant to toggle.
        if (portal != null && ps.doTeleportPlayer(Type.VEHICLE_MOVE) && !ps.showDebugInfo()) {
            if (!ps.allowTeleportViaCooldown(new Date())) {
                p.sendMessage(ps.getFriendlyRemainingTimeMessage());
                return false;
            }
            // TODO: Money
            MVDestination d = portal.getDestination();
            if (d == null || d instanceof InvalidDestination) {
                return false;
            }

            Location l = d.getLocation(p);
            Vector vehicleVec = v.getVelocity();

            // 0 Yaw in dest = 0,X
            if (d instanceof PortalDestination) {
                PortalDestination pd = (PortalDestination) d;

                // Translate the direction of travel.
                vehicleVec = LocationManipulation.getTranslatedVector(vehicleVec, pd.getOrientationString());
            }

            // Set the velocity
            // Will set to the destination's velocity if one is present
            // Or
            this.setVehicleVelocity(vehicleVec, d, v);

            p.setFallDistance(0);

            SafeTTeleporter playerTeleporter = new SafeTTeleporter(this.plugin.getCore());

            // The worlds are different! Ahhh!
            if (!l.getWorld().equals(p.getWorld())) {
                return teleportVehicleSeperately(p, v, d, ps, playerTeleporter);
            }

            if (playerTeleporter.safelyTeleport(p, v, d)) {
                ps.playerDidTeleport(to);
                ps.setTeleportTime(new Date());
            }
            return true;
        }
        return false;
    }

    private boolean teleportVehicleSeperately(Player p, Vehicle v, MVDestination to, PortalPlayerSession ps, SafeTTeleporter tp) {
        // Remove the player from the old one.
        v.eject();
        Location toLocation = to.getLocation(v);
        // Add an offset to ensure the player is 1 higher than where the cart was.
        to.getLocation(p).add(0, .5, 0);
        // If they didn't teleport, return false and place them back into their vehicle.
        if (!tp.safelyTeleport(p, p, to)) {
            v.setPassenger(p);
            return false;
        }

        // Now create a new vehicle:
        Vehicle newVehicle = toLocation.getWorld().spawn(toLocation, v.getClass());

        // Set the vehicle's velocity to ours.
        this.setVehicleVelocity(v.getVelocity(), to, newVehicle);

        // Set the new player
        newVehicle.setPassenger(p);

        // They did teleport. Let's delete the old vehicle.
        v.remove();

        return true;
    }

    private void setVehicleVelocity(Vector calculated, MVDestination to, Vehicle newVehicle) {
        // If the destination has a non-zero velocity, use that,
        // otherwise use the existing velocity, because velocities
        // are preserved through portals... duh.
        if (!to.getVelocity().equals(new Vector(0, 0, 0))) {
            newVehicle.setVelocity(to.getVelocity());
        } else {
            newVehicle.setVelocity(calculated);
        }
    }
}
