package me.matoosh.softclaims.exception.faction;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class NotEnoughPowerException extends Exception {
    private final double powerDeficiency;
}
