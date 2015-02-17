package com.jdroid.gradle.android
import com.android.build.gradle.AppPlugin
import com.jdroid.gradle.android.task.CountMethodsReportTask
import com.jdroid.gradle.android.task.CountMethodsSummaryTask
import com.jdroid.gradle.android.task.IncrementMajorVersionTask
import com.jdroid.gradle.android.task.IncrementMinorVersionTask
import com.jdroid.gradle.android.task.IncrementPatchVersionTask
import org.gradle.api.Project
public class AndroidApplicationGradlePlugin extends AndroidGradlePlugin {

	public void apply(Project project) {
		super.apply(project);

		project.task('countMethodsReport', type: CountMethodsReportTask)
		project.task('countMethodsSummary', type: CountMethodsSummaryTask)
		project.task('incrementMajorVersion', type: IncrementMajorVersionTask)
		project.task('incrementMinorVersion', type: IncrementMinorVersionTask)
		project.task('incrementPatchVersion', type: IncrementPatchVersionTask)
	}

	protected Class<? extends AndroidGradlePluginExtension> getExtensionClass() {
		return AndroidApplicationGradlePluginExtension.class;
	}

	protected void applyAndroidPlugin() {
		project.apply plugin: AppPlugin
	}
}

public class AndroidApplicationGradlePluginExtension extends AndroidGradlePluginExtension {

	def versionMajor
	def versionMinor
	def versionPatch

}