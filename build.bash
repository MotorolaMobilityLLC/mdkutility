#!/bin/bash
#-------------------------------------------------------------------------------
#  build.bash - phoenix application build script
#-------------------------------------------------------------------------------

# to make bash scripts behave like makefiles, exit on any error
set -e

script_name=$(basename ${0})

function usage {
    echo ""
    echo "Usage: ${script_name} -d -p -a -s -f"
    echo "      d: debug"
    echo "      p: use mmi proxy"
    echo "      a: ant build"
    echo "      s: subproject, eg. MotCamera, wear"
    echo "      f: build flavor, eg. myresConfigs"
    echo ""
    exit 1
}


while getopts dpas:f: o
do
    case "$o" in
        d)  set -x ;;
        p)  mmi_proxy=true ;;
        a)  ant_build=true ;;
        s)  sub_project="$OPTARG" ;;
        f)  build_flavor="$OPTARG" ;;
        [?]) usage ;;
    esac
done

script_dir=$(cd $(dirname ${0}); pwd)
top_dir=$(cd ${script_dir}; pwd)

if [ ! -e ${script_dir}/build.conf ]; then
    cd ${script_dir}
    if [ -d ${script_dir}/build ]; then
        rm -rf ${script_dir}/build
    fi
    git clone ssh://gerrit.mot.com/home/repo/dev/apps/build.git -b main-l-5.1 --single-branch
    source ${script_dir}/build/build-common.conf
else
    source ${script_dir}/build.conf
fi

# If we are in the mmi network use a proxy
if [ "${mmi_proxy}" == "true" ];then
    wget_proxy_argument="--execute=http_proxy=wwwgate0.mot.com:1080"
    android_proxy_argument="--proxy-host wwwgate0.mot.com --proxy-port 1080"
fi

if [ ! -d ${android_sdk_dir} ]; then
    if [ "${mmi_proxy}" == "true" ];then
        wget_command="wget"
        wget_command="${wget_command} ${wget_proxy_argument}"
        ${wget_command} http://dl.google.com/android/${android_sdk_starter_pkg} -O ${top_dir}/${android_sdk_starter_pkg}
        if [ "${operating_system}" == "Darwin" ]; then
            unzip ${top_dir}/${android_sdk_starter_pkg} -d ${top_dir}
        elif [ "${operating_system}" == "Linux" ]; then
            tar xfz ${top_dir}/${android_sdk_starter_pkg} --directory ${top_dir}
        fi
    else
        android_sdk_branch=${android_sdk_starter_pkg_prefix}
        if [ "${operating_system}" == "Darwin" ]; then
            android_sdk_branch=${android_sdk_branch}-macosx
            git clone ssh://gerrit.mot.com/home/repo/dev/AndroidSDK.git -b ${android_sdk_branch} --single-branch
            mv AndroidSDK ${android_sdk_dir}
        elif [ "${operating_system}" == "Linux" ]; then
            android_sdk_branch=${android_sdk_branch}-linux
            git clone ssh://gerrit.mot.com/home/repo/dev/AndroidSDK.git -b ${android_sdk_branch} --single-branch
            mv AndroidSDK ${android_sdk_dir}
        fi
    fi
fi

## get NDK
#if [ ! -d ${android_ndk_dir} ]; then
#    wget_command="wget"
#
#    ${wget_command} http://dl.google.com/android/ndk/${android_ndk_starter_pkg} -O ${top_dir}/${android_ndk_starter_pkg}
#    if [ "${operating_system}" == "Darwin" ]; then
#        bzip2 -dc ${top_dir}/${android_ndk_starter_pkg} | tar xf - --directory ${top_dir}
#    elif [ "${operating_system}" == "Linux" ]; then
#        bzip2 -dc ${top_dir}/${android_ndk_starter_pkg} | tar xf - --directory ${top_dir}
#    fi
#fi

export ANDROID_HOME=${android_sdk_dir}

# Version is <major>.<minor>.<build_number>
# AndroidVersionCode is an INT32 so MAX 4,294,967,295
# Defining the schema using 9 digits <P><2><2><5>
# where <P> is one digit which can have the value 0 to 3. We leave it to 0 for now.

# hss7 is using 2 as Major version
VERSION_MAJOR=2
VERSION_MINOR=1
VERSION_CODE=`printf "%d%02d%05d" $VERSION_MAJOR $VERSION_MINOR $BUILD_NUMBER`
VERSION_NAME=`printf "%d.%d.%d" $VERSION_MAJOR $VERSION_MINOR $BUILD_NUMBER`

# Have android tool properly set sdk.dir property in local.properties file
application_dir=${top_dir}

if [ "${ant_build}" == "true" ];then
    cd ${application_dir}
    ant clean release -Dversion.code=$VERSION_CODE -Dversion.name=$VERSION_NAME
else
    cd ${top_dir}
    if [ -z $sub_project ]; then
        if [ -z $build_flavor ]; then
            ${top_dir}/gradlew --refresh-dependencies clean build
        else
            if [ -z $GRADLE_PRODUCT_AAPT_CONFIG ]; then
                echo "No value of GRADLE_PRODUCT_AAPT_CONFIG, please check"
            else
                ${top_dir}/gradlew --refresh-dependencies clean assemble${build_flavor}
            fi
        fi
    else
        if [ -z $build_flavor ]; then
            ${top_dir}/gradlew --refresh-dependencies clean build -p $sub_project
        else
            if [ -z $GRADLE_PRODUCT_AAPT_CONFIG ]; then
                echo "No value of GRADLE_PRODUCT_AAPT_CONFIG, please check"
            else
                ${top_dir}/gradlew --refresh-dependencies clean -p $sub_project assemble${build_flavor}
            fi
        fi
    fi
fi

unaligned_files=$(find . -name *.apk | grep unaligned)
rm $unaligned_files
