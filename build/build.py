#!/usr/bin/python

"""Copyright (c) 2011 The Chromium Authors. All rights reserved.

Use of this source code is governed by a BSD-style license that can be
found in the LICENSE file.

Eclipse Dart Editor buildbot steps.
"""

import glob
import optparse
import os
import shutil
import subprocess
import sys
import tempfile
import gsutil
import postprocess


class AntWrapper(object):
  """Class to abstract the ant calls from the program."""

  _antpath = None
  _bzippath = None
  _propertyfile = None

  def __init__(self, propertyfile, antpath='/usr/bin', bzippath=None):
    """Initialize the class with the ant path.

    Args:
      propertyfile: the file to write the build properties to
      antpath: the path to ant
      bzippath: the path to the bzip jar

    """
    self._antpath = antpath
    self._bzippath = bzippath
    self._propertyfile = propertyfile
    print 'AntWrapper.__init__({0}, {1}, {2})'.format(self._propertyfile,
                                                      self._antpath,
                                                      self._bzippath)

  def RunAnt(self, build_dir, antfile, revision, name,
             buildroot, buildout, sourcepath, buildos,
             extra_args=None):
    """Run the given Ant script from the given directory.

    Args:
      build_dir: the directory to run the ant script from
      antfile: the ant file to run
      revision: the SVN revision of this build
      name: the name of the builder
      buildroot: root of the build source tree
      buildout: the location to copy output
      sourcepath: the path to the root of the source
      buildos: the operating system this build is running under (may be null)
      extra_args: any extra args to ant

    Returns:
      returns the status of the ant call

    Raises:
      Exception: if a shell can not be found
    """
    os_shell = '/bin/bash'
    ant_exec = 'ant'
    is_windows = False
    if not os.path.exists(os_shell):
      os_shell = os.environ['COMSPEC']
      if os_shell is None:
        raise Exception('could not find shell')
      else:
        ant_exec = 'ant.bat'
        is_windows = True

    cwd = os.getcwd()
    os.chdir(build_dir)
    print 'cwd = {0}'.format(os.getcwd())
    print 'ant path = {0}'.format(self._antpath)
    # run the ant file given
    local_env = os.environ
    args = []
    if not is_windows:
      args.append(os_shell)
    args.append(os.path.join(self._antpath, ant_exec))
    args.append('-lib')
    args.append(os.path.join(self._bzippath, 'bzip2.jar'))
    args.append('-noinput')
    args.append('-nouserlib')
    if antfile:
      args.append('-f')
      args.append(antfile)
    if revision:
      args.append('-Dbuild.revision=' + revision)
    if name:
      args.append('-Dbuild.builder=' + name)
    if buildroot:
      args.append('-Dbuild.root=' + buildroot)
    if buildout:
      args.append('-Dbuild.out=' + buildout)
    if sourcepath:
      args.append('-Dbuild.source=' + sourcepath)
    if self._propertyfile:
      args.append('-Dbuild.out.property.file=' + self._propertyfile)
    if buildos:
      args.append('-Dbuild.os={0}'.format(buildos))
    if is_windows:
      args.append('-autoproxy')
      #add the JAVA_HOME to the environment for the windows builds
      local_env['JAVA_HOME'] = 'C:\Program Files\Java\jdk1.6.0_29'
    if extra_args:
      args.extend(extra_args)

    extra_args = os.environ.get('ANT_EXTRA_ARGS')
    if extra_args is not None:
      parsed_extra = extra_args.split()
      for arg in parsed_extra:
        args.append(arg)

    print ' '.join(args)
    status = subprocess.call(args, shell=is_windows)
    os.chdir(cwd)
    return status


def _BuildOptions():
  """Setup the argument processing for this program."""
  result = optparse.OptionParser()
  result.set_default('dest', 'gs://dart-editor-archive-continuous')
  result.add_option('-m', '--mode',
                    help='Build variants (comma-separated).',
                    metavar='[all,debug,release]',
                    default='debug')
  result.add_option('-v', '--verbose',
                    help='Verbose output.',
                    default=False, action='store')
  result.add_option('-r', '--revision',
                    help='SVN Revision.',
                    action='store')
  result.add_option('-n', '--name',
                    help='builder name.',
                    action='store')
  result.add_option('-o', '--out',
                    help='Output Directory.',
                    action='store')
  result.add_option('--dest',
                    help='Output Directory.',
                    action='store')
  return result


def GetUtils(toolspath):
  """Dynamically get the utils module.

  We use a dynamic import for tools/util.py because we derive its location
  dynamically using sys.argv[0]. This allows us to run this script from
  different directories.

  Args:
    toolspath: the path to the tools directory

  Returns:
    the utils module
  """
  sys.path.append(os.path.abspath(toolspath))
  utils = __import__('utils')
  return utils


def main():
  """Main entry point for the build program."""

  if not sys.argv:
    print 'Script pathname not known, giving up.'
    return 1

  scriptdir = os.path.dirname(sys.argv[0])
  editorpath = os.path.abspath(os.path.join(scriptdir, '..'))
  thirdpartypath = os.path.abspath(os.path.join(scriptdir, '..', '..',
                                                'third_party'))
  toolspath = os.path.abspath(os.path.join(scriptdir, '..', '..',
                                           'tools'))
  dartpath = os.path.abspath(os.path.join(scriptdir, '..', '..'))
  antpath = os.path.join(thirdpartypath, 'apache_ant', 'v1_7_1')
  bzip2libpath = os.path.join(thirdpartypath, 'bzip2')
  buildpath = os.path.join(editorpath, 'tools', 'features',
                           'com.google.dart.tools.deploy.feature_releng')
  buildroot = os.path.join(editorpath, 'build_root')
  utils = GetUtils(toolspath)
  buildos = utils.GuessOS()
  print 'buildos        = {0}'.format(buildos)
  print 'scriptdir      = {0}'.format(scriptdir)
  print 'editorpath     = {0}'.format(editorpath)
  print 'thirdpartypath = {0}'.format(thirdpartypath)
  print 'toolspath      = {0}'.format(toolspath)
  print 'antpath        = {0}'.format(antpath)
  print 'bzip2libpath   = {0}'.format(bzip2libpath)
  print 'buildpath      = {0}'.format(buildpath)
  print 'buildroot      = {0}'.format(buildroot)
  print 'dartpath       = {0}'.format(dartpath)

  os.chdir(buildpath)
  ant_property_file = None
  try:
    ant_property_file = tempfile.NamedTemporaryFile(suffix='.property',
                                                    prefix='AntProperties',
                                                    delete=False)
    ant_property_file.close()
    ant = AntWrapper(ant_property_file.name, os.path.join(antpath, 'bin'),
                     bzip2libpath)

    ant.RunAnt(os.getcwd(), '', '', '', '',
               '', '', buildos, ['-diagnostics'])

    homegsutil = os.path.join(os.path.expanduser('~'), 'gsutil', 'gsutil')
    gsu = gsutil.GsUtil(False, homegsutil)

    parser = _BuildOptions()
    (options, args) = parser.parse_args()
    # Determine which targets to build. By default we build the "all" target.
    if args:
      print 'only options should be passed to this script'
      parser.print_help()
      return 1

    if str(options.revision) == 'None':
      print 'missing revision option'
      parser.print_help()
      return 2

    if str(options.name) == 'None':
      print 'missing builder name'
      parser.print_help()
      return 2

    if str(options.out) == 'None':
      print 'missing output directory'
      parser.print_help()
      return 2

    #this code handles getting the revision on the developer machine
    #where it can be 123, 123M 123:125M
    revision = options.revision
    lastc = revision[-1]
    if lastc.isalpha():
      revision = revision[0:-1]
    index = revision.find(':')
    if index > -1:
      revision = revision[0:index]
    print 'revision       = {0}'.format(revision)

    buildout = os.path.join(buildroot, options.out)

    #get user name if it does not start with chrome then deploy
    # to the test bucket otherwise deploy to the continuous bucket
    #I could not find any non-OS specific way to get the user under Python
    # so the environemnt variables 'USER' Linux and Mac and
    # 'USERNAME' Windows were used.
    username = os.environ.get('USER')
    if username is None:
      username = os.environ.get('USERNAME')

    if username is None:
      _PrintError('could not find the user name'
                  ' tried environment variables'
                  ' USER and USERNAME')
      return 1
    sdk_environment = os.environ
    if username.startswith('chrome'):
      from_bucket = 'gs://dart-dump-render-tree'
      to_bucket = 'gs://dart-editor-archive-continuous'
      run_sdk_build = True
      running_on_buildbot = True
    else:
      from_bucket = 'gs://dart-editor-archive-testing'
      to_bucket = 'gs://dart-editor-archive-testing'
      run_sdk_build = False
      running_on_buildbot = False
      sdk_environment['DART_LOCAL_BUILD'] = 'dart-editor-archive-testing'

    #this is a hack to allow the SDK build to be run on a local machine
    if sdk_environment.has_key('FORCE_RUN_SDK_BUILD'):
      run_sdk_build = True
    #end hack

    print '@@@BUILD_STEP dart-ide dart clients: %s@@@' % options.name
    if sdk_environment.has_key('JAVA_HOME'):
      print 'JAVA_HOME = {0}'.format(str(sdk_environment['JAVA_HOME']))
    builder_name = str(options.name)

    if (run_sdk_build and
        builder_name != 'dart-editor'):
      _PrintSeparator('running the build of the Dart SDK')
      dartbuildscript = os.path.join(toolspath, 'build.py')
      cmds = [sys.executable, dartbuildscript,
              '--mode=release', 'upload_sdk']
      cwd = os.getcwd()
      try:
        os.chdir(dartpath)
        print ' '.join(cmds)
        status = subprocess.call(cmds, env=sdk_environment)
        print 'sdk build returned ' + str(status)
        if status:
          _PrintError('the build of the SDK failed')
          return status
      finally:
        os.chdir(cwd)
      _CopySdk(buildos, revision, to_bucket, from_bucket, gsu)

    if builder_name == 'dart-editor':
      buildos = None
  #  else:
  #    _PrintSeparator('new builder running on {0} is'
  #                    ' a place holder until the os specific builds'
  #                    ' are in place.  This is a '
  #                    'normal termination'.format(builder_name))
  #    return 0

    _PrintSeparator('running the build to produce the Zipped RCP''s')
    status = ant.RunAnt('.', 'build_rcp.xml', revision, options.name,
                        buildroot, buildout, editorpath, buildos)
    #the ant script writes a property file in a known location so
    #we can read it. This build script is currently not using any post
    #processing
    properties = _ReadPropertyFile(ant_property_file.name)

    if not properties:
      raise Exception('no data was found in file {0}'.
                      format(ant_property_file.name))
    if status and properties['build.runtime']:
      _PrintErrorLog(properties['build.runtime'])
      #This build script is currently not using any post processing
      #so this line is commented out
      # If the preprocessor needs to be run in the
      #  if not status and properties['build.tmp']:
      #    postProcessZips(properties['build.tmp'], buildout)
    sys.stdout.flush()
    if status:
      return status

    #return on any builder but dart-editor
    if buildos:
      found_zips = _FindRcpZipFiles(properties['build.out'])
      if not found_zips:
        _PrintError('could not find any zipped up RCP files.'
                    '  The Ant build must have failed')
        return 1
      else:
        _DeployRcpsToTest(buildos, 'gs://dart-editor-archive-testing/testing',
                          found_zips, gsu)
      return 0

    #if the build passed run the deploy artifacts
    _PrintSeparator("Deploying the built RCP's to Google Storage")
    status = _DeployArtifacts(buildout, to_bucket,
                              properties['build.tmp'], revision,
                              gsu)
    if status:
      return status

    _PrintSeparator("Setting the ACL'sfor the RCP's in Google Storage")
    _SetAclOnArtifacts(to_bucket,
                       [revision + '/DartBuild', 'latest/DartBuild'],
                       gsu)

    sys.stdout.flush()

    _PrintSeparator('Running the tests')
    status = ant.RunAnt('../com.google.dart.tools.tests.feature_releng',
                        'buildTests.xml',
                        revision, options.name, buildroot, buildout,
                        editorpath, buildos)
    properties = _ReadPropertyFile(ant_property_file.name)
    if status and properties['build.runtime']:
      #if there is a build.runtime and the status is not
      #zero see if there are any *.log entries
      _PrintErrorLog(properties['build.runtime'])
    return status
  finally:
    if ant_property_file is not None:
      print 'cleaning up temp file {0}'.format(ant_property_file.name)
      os.remove(ant_property_file.name)


def _ReadPropertyFile(property_file):
  """Read a property file and return a dictionary of key/value pares.

  Args:
    property_file: the file to read

  Returns:
    the dictionary of Ant properties
  """
  properties = {}
  print 'processing file ' + property_file
  for line in open(property_file):
    #ignore comments
    if not line.startswith('#'):
      parts = line.split('=')
      key = str(parts[0]).strip()
      value = str(parts[1]).strip()
      properties[key] = value

  return properties


def _PostProcessZips(tmpdir, buildout):
  """Run the post processor on the zipfiles.

  Args:
    tmpdir: the location to work on the files
    buildout: the location of the zip files
  """
  #copy the zip files to a new temp directory
  workdir = os.path.join(tmpdir.strip(), 'postprocess')
  os.makedirs(workdir)
  print 'copying zip files from %s to %s' % (buildout, workdir)
  for zipfile in glob.glob(os.path.join(buildout, '*.zip')):
    shutil.copy(zipfile, os.path.join(workdir, os.path.basename(zipfile)))
  #process the zip files to add any files
  postprocess.processZips(workdir)
  #copy the zip files back
  print 'copying zip files from %s to %s' % (workdir, buildout)
  for zipfile in glob.glob(os.path.join(workdir, '*.zip')):
    shutil.copy(zipfile, os.path.join(buildout, os.path.basename(zipfile)))


def _PrintErrorLog(rootdir):
  """Print an eclipse error log if one is found.

  Args:
    rootdir: the directory to start from
  """
  print 'search ' + rootdir + ' for error logs'
  found = False
  configdir = os.path.join(rootdir, 'eclipse', 'configuration')
  if os.path.exists(configdir):
    for logfile in glob.glob(os.path.join(configdir, '*.log')):
      print 'Found log file: ' + logfile
      found = True
      for logline in open(logfile):
        print logline
  if not found:
    print 'no log file was found in ' + configdir


def _DeployArtifacts(fromd, to, tmp, svnid, gsu):
  """Deploy the artifacts (zipped RCP applications) to Google Storage.

  This function copies the artifacts to two places
  gs://dart-editor-archive-continuous/svnid and
  gs://dart-editor-archive-continuous/latest.
  Google Storage Does not have sym links so we have to make two
  copies of the deployed artifacts so there will always be a
  constant continuous URL.

  Args:
    fromd: directory the zipped RCP applications are located
    to: the base location in Google Storage
    tmp: the temporary working directory
    svnid: the svn revision number for this build
    gsu: the gsutil wrapper object

  Returns:
    the status of the gsutil copy to Google Storage
  """
  print ('deploying zips in {0} to {1}'
         ' (tmp: {2} svnID: {3})').format(str(fromd), str(to),
                                          str(tmp), str(svnid))
  cwd = os.getcwd()
  deploydir = None
  status = None
  print 'deploying to {0}'.format(to)
  try:
    os.chdir(tmp)
    deploydir = os.path.join(tmp, str(svnid))
    print 'creating directory ' + deploydir
    os.makedirs(deploydir)
    artifacts = []
    for zipfile in glob.glob(os.path.join(fromd, '*.zip')):
      artifacts.append(zipfile)
      shutil.copy2(zipfile, deploydir)

    status = gsu.Copy(svnid, to, False, True)
    if status:
      _PrintError('the push to Google Storage of {0} failed'.format(svnid))
    else:
      deploydir = os.path.join(tmp, 'latest')
      shutil.move(svnid, 'latest')
      status = gsu.Copy('latest', to, True, True)
      if status:
        _PrintError('the push to Google Storage of latest failed')
      else:
        print ('code Successfully deployed to:'
               '{2}\t{0}/{1}{2}\t{0}/latest').format(to, svnid, os.linesep)
        print 'The URL\'s for the artifacts:'
        for artifact in artifacts:
          print '  {1} -> {0}/latest/{1}'.format(to, os.path.basename(artifact))
        print
        print 'the console for Google storage for this project can be found at'
        print ('https://sandbox.google.com/storage/?project=375406243259'
               '&pli=1#dart-editor-archive-continuous')
    sys.stdout.flush()

  finally:
    os.chdir(cwd)
    if deploydir:
      shutil.rmtree(deploydir)

  return status


def _DeployRcpsToTest(build_os, to_bucket, zip_files, gsu):
  """Deploy the build RCP's to the test bucket.

  Args:
    build_os: the os for this build
    to_bucket: the location on GoogleStorage to copy the files
    zip_files: list of zip files to copy to GoogleStorage
    gsu: the GoogleStorage wrapper
  """
  print '_DeployRcpsToTest({0}, {1}, {2}, gsu)'.format(to_bucket, zip_files,
                                                       build_os)
  for element in zip_files:
    base_name = os.path.basename(element)
    to = '{0}/{1}/{2}'.format(to_bucket, build_os, base_name)
    status = gsu.Copy(element, to)
    if not status:
      _SetAcl(to, gsu)


def _SetAclOnArtifacts(to, bucket_tags, gsu):
  """Set the ACL's on the GoogleStorage Objects.

  Args:
    to: the bucket that holds the objects
    bucket_tags: list of directory(s) on google storage to change the ACL's on
    gsu: the gsutil wrapper object
  """
  print ('setting ACL''s on objects in'
         ' bucket {0} matching {1}').format(to, bucket_tags)

  contents = gsu.ReadBucket(to)
  for element in contents:
    for tag in bucket_tags:
      if tag in element:
        _SetAcl(element, gsu)


def _SetAcl(element, gsu):
  """Set the ACL on a GoogleStorage object.

  Args:
    element: the object to set the ACL on
    gsu: the gsutil object
  """
  print 'setting ACL on {0}'.format(element)
  #These lines are being commented out because the windows build is having
  # an issue parsing the XML that comes back from the gsu.GetAcl() command.
  # The workaround is to use a static ACL from the acl.xml file to set the
  # to set the ACL's for the given object.
#  gsu.SetCannedAcl(element, 'project-private')
#  acl = gsu.GetAcl(element)
#  print 'acl = {0}'.format(acl)
#  acl = gsu.AddPublicAcl(acl)
  aclfile = os.path.abspath(os.path.join('..', '..', '..', 'build', 'acl.xml'))
  gsu.SetAclFromFile(element, aclfile)


def _CopySdk(buildos, revision, bucket_to, bucket_from, gsu):
  """Copy the deployed SDK to the editor buckets.

  Args:
    buildos: the OS the build is running under
    revision: the svn revision
    bucket_to: the bucket to upload to
    bucket_from: the bucket to copy the sdk from
    gsu: the gsutil object
  """
  print '_CopySdk({0}, {1}, {2}, gsu)'.format(buildos, revision, bucket_to)
  sdkfullzip = 'dart-{0}-{1}.zip'.format(buildos, revision)
  sdkshortzip = 'dart-{0}.zip'.format(buildos)
  gssdkzip = '{0}/sdk/{1}'.format(bucket_from, sdkfullzip)
  gseditorzip = '{0}/{1}/{2}'.format(bucket_to, revision, sdkshortzip)
  gseditorlatestzip = '{0}/{1}/{2}'.format(bucket_to, 'latest', sdkshortzip)

  print 'copying {0} to {1}'.format(gssdkzip, gseditorzip)
  gsu.Copy(gssdkzip, gseditorzip)
  _SetAcl(gseditorzip, gsu)
  print 'copying {0} to {1}'.format(gssdkzip, gseditorlatestzip)
  gsu.Copy(gssdkzip, gseditorlatestzip)
  _SetAcl(gseditorlatestzip, gsu)


def _FindRcpZipFiles(out_dir):
  """Fint the Zipped RCP files.

  Args:
    out_dir: the directory the files will be located in

  Returns:
    a collection of rcp zip files
  """
  rcp_out_dir = os.listdir(out_dir)
  found_zips = []
  for element in rcp_out_dir:
    if element.startswith('DartBuild') and element.endswith('.zip'):
      found_zips.append(os.path.join(out_dir, element))
  return found_zips


def _PrintSeparator(text):
  """Print a separator for the build steps."""

  #used to print separators during the build process
  tag_line_sep = '================================'
  tag_line_text = '= {0}'

  print tag_line_sep
  print tag_line_sep
  print tag_line_text.format(text)
  print tag_line_sep
  print tag_line_sep


def _PrintError(text):
  """Print an error message."""
  error_sep = '*****************************'
  error_text = ' {0}'

  print error_sep
  print error_sep
  print error_text.format(text)
  print error_sep
  print error_sep


if __name__ == '__main__':
  sys.exit(main())
