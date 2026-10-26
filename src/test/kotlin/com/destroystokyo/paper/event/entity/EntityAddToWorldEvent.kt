/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.destroystokyo.paper.event.entity

/** Test marker whose initialiser must never run during platform detection. */
class EntityAddToWorldEvent {
    companion object {
        init {
            error("Platform discovery initialised the marker")
        }
    }
}
