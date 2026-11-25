package app.plansnap.di

import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.domain.command.CommandHistory
import app.plansnap.core.domain.command.DefaultCommandHistory
import app.plansnap.core.domain.command.DefaultProjectCommandFactory
import app.plansnap.core.domain.command.DockPlacementResolver
import app.plansnap.core.domain.command.ProjectCommandFactory
import app.plansnap.core.domain.evaluation.DefaultRoomEvaluator
import app.plansnap.core.domain.evaluation.RoomEvaluator
import app.plansnap.core.domain.opening.DefaultOpeningRules
import app.plansnap.core.domain.placement.DefaultDockPlacementResolver
import app.plansnap.core.domain.report.AreaReportCalculator
import app.plansnap.core.domain.report.DefaultAreaReportCalculator
import app.plansnap.core.domain.snap.DefaultSnapper
import app.plansnap.core.domain.snap.Snapper
import app.plansnap.core.domain.validation.DefaultProjectValidator
import app.plansnap.core.domain.validation.OpeningRules
import app.plansnap.core.domain.validation.ProjectValidator
import app.plansnap.core.model.Project
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Domain services. All are stateless and shared, except the command history, which exists once per opened project. */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    /** Room evaluation. */
    @Provides
    @Singleton
    fun roomEvaluator(dispatchers: DispatcherProvider): RoomEvaluator = DefaultRoomEvaluator(dispatchers)

    /** Opening rules shared by the command factory and the validator. */
    @Provides
    @Singleton
    fun openingRules(): OpeningRules = DefaultOpeningRules()

    /** Validator of whole projects, used when storing and importing. */
    @Provides
    @Singleton
    fun projectValidator(): ProjectValidator = DefaultProjectValidator()

    /** Factory of the commands of all edits. */
    @Provides
    @Singleton
    fun projectCommandFactory(openingRules: OpeningRules): ProjectCommandFactory =
        DefaultProjectCommandFactory(openingRules)

    /** Resolver of dock placements, used by every command history. */
    @Provides
    @Singleton
    fun dockPlacementResolver(evaluator: RoomEvaluator): DockPlacementResolver = DefaultDockPlacementResolver(evaluator)

    /** Magnetic snapping of drag gestures. */
    @Provides
    @Singleton
    fun snapper(): Snapper = DefaultSnapper()

    /** Area report of a project. */
    @Provides
    @Singleton
    fun areaReportCalculator(): AreaReportCalculator = DefaultAreaReportCalculator()

    /**
     * Factory of the session history of one opened project. Every call returns a new, independent history, so that a
     * project never shares undo steps with another.
     */
    @Provides
    @Singleton
    fun commandHistoryFactory(
        evaluator: RoomEvaluator,
        resolver: DockPlacementResolver,
        dispatchers: DispatcherProvider,
    ): (Project) -> CommandHistory = { project -> DefaultCommandHistory(project, evaluator, resolver, dispatchers) }
}
