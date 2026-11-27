package app.plansnap.feature.editor.rooms

/** Edits of the open dock sheet: each intent of the sheet changes one choice of the [DockDraft]. */
internal object DockEditing {
    /**
     * Returns [draft] after [intent], `null` if the sheet closes. Intents that do not belong to the sheet leave the
     * draft as it is. The validity of the result is recomputed by [DockDrafts.validated].
     */
    fun apply(
        draft: DockDraft,
        intent: RoomsIntent,
    ): DockDraft? =
        when (intent) {
            RoomsIntent.Dismiss -> null
            RoomsIntent.StartPicking -> draft.copy(picking = true)
            RoomsIntent.StopPicking -> draft.copy(picking = false)
            is RoomsIntent.SelectHostRoom -> selectHostRoom(draft, intent)
            is RoomsIntent.SelectHost -> selectHost(draft, intent)
            is RoomsIntent.SelectOwnWall -> draft.copy(ownWallId = intent.wallId)
            is RoomsIntent.OffsetChanged -> draft.copy(offsetText = intent.text)
            is RoomsIntent.OffsetNegative -> draft.copy(offsetNegative = intent.negative)
            else -> draft
        }

    private fun selectHostRoom(
        draft: DockDraft,
        intent: RoomsIntent.SelectHostRoom,
    ): DockDraft =
        if (intent.roomId == draft.hostRoomId) {
            draft
        } else {
            draft.copy(hostRoomId = intent.roomId, hostWallId = null, hostOpeningId = null)
        }

    private fun selectHost(
        draft: DockDraft,
        intent: RoomsIntent.SelectHost,
    ): DockDraft =
        draft.copy(
            hostRoomId = intent.roomId,
            hostWallId = intent.wallId,
            hostOpeningId = intent.openingId,
            picking = false,
        )
}
